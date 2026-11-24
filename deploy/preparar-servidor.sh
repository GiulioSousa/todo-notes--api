#!/usr/bin/env bash
#
# Preparação do servidor para o todo-notes. Roda UMA VEZ, como root, antes do primeiro
# deploy. Pode ser repetido: cada etapa confere o que já existe antes de criar.
#
# O QUE FAZ
#
#   1. Confere o terreno: JDK 25, PostgreSQL na 5432, portas livres, claude-todonotes existe.
#   2. Cria o usuário de sistema todonotes, dono de /opt/todonotes e de /var/www/todonotes.
#   3. Clona os dois repositórios públicos em /opt/todonotes, como todonotes.
#   4. Gera as duas senhas do banco e a chave JWT AQUI, direto no /etc/todonotes/todonotes.env
#      (root:root 600). Nenhuma delas aparece na tela, no chat ou no Git.
#   5. Cria o banco todonotes_db e as duas roles, pelo db/criar-roles.sql, com as senhas
#      geradas — sem gravar cópia preenchida do script em disco.
#   6. Instala a unit do systemd (habilitada, NÃO iniciada: o jar ainda não existe).
#   7. Instala o site do nginx (só HTTP; o certbot vem depois) e recarrega, com `nginx -t` antes.
#   8. Instala a regra de sudo do claude-todonotes, com `visudo -cf` antes.
#
# O QUE NÃO FAZ
#
#   Não mexe no GeFinX, na oficina, no firewall nem no DNS. Não inicia a aplicação. Não roda
#   o certbot, que precisa do registro DNS já propagado e pergunta coisas.
#
# COMO RODAR
#
#   bash preparar-servidor.sh
#   DOMINIO=outro.keystech.dev bash preparar-servidor.sh   # se o subdomínio for outro

set -euo pipefail
cd /

DOMINIO="${DOMINIO:-todo-notes.keystech.dev}"
PORTA_API=8092
PORTA_MONITORAMENTO=8093

REPO_API=https://github.com/GiulioSousa/todo-notes--api.git
REPO_FRONTEND=https://github.com/GiulioSousa/todo-notes--frontend.git

JAVA=/usr/lib/jvm/java-25-openjdk-amd64/bin/java
BASE=/opt/todonotes
ENV_DIR=/etc/todonotes
ENV_FILE=$ENV_DIR/todonotes.env
SITE=/var/www/todonotes
NGINX_SITE=/etc/nginx/sites-available/todonotes

etapa() { printf '\n== %s\n' "$*"; }
ok() { printf '   ok: %s\n' "$*"; }
falha() { printf '\nERRO: %s\n' "$*" >&2; exit 1; }
como_todonotes() { sudo -u todonotes -H "$@"; }
psql_postgres() { sudo -u postgres psql -X -v ON_ERROR_STOP=1 "$@"; }

[ "$(id -u)" -eq 0 ] || falha "rode como root"

# ---------------------------------------------------------------------------------------
etapa "1. Conferências"

[ -x "$JAVA" ] || falha "JDK 25 não encontrado em $JAVA"
ok "JDK 25 em $JAVA"

for programa in git nginx psql pg_lsclusters node npm openssl visudo ss; do
  command -v "$programa" >/dev/null || falha "$programa não encontrado"
done
ok "git, nginx, psql, node $(node --version), npm $(npm --version)"

pg_lsclusters --no-header | awk '$3 == 5432 && $4 == "online" { achou = 1 } END { exit !achou }' \
  || falha "nenhum cluster PostgreSQL online na 5432 (pg_lsclusters)"
ok "PostgreSQL: $(pg_lsclusters --no-header | awk '$3 == 5432 { print $1 "/" $2 }') na 5432"

# Numa segunda execução, as portas estão ocupadas pelo próprio todo-notes: isso não é erro.
if ! systemctl is-active --quiet todonotes 2>/dev/null; then
  for porta in "$PORTA_API" "$PORTA_MONITORAMENTO"; do
    if ss -Htln "sport = :$porta" | grep -q .; then
      ss -Htlnp "sport = :$porta"
      falha "a porta $porta já está em uso. Escolha outra no script, no todonotes.env e no nginx."
    fi
  done
fi
ok "portas $PORTA_API e $PORTA_MONITORAMENTO livres (ou do próprio todo-notes)"

id claude-todonotes >/dev/null 2>&1 || falha "o usuário claude-todonotes não existe — crie-o antes, pelo roteiro (seção 3.2)"
ok "claude-todonotes existe"

free -h

# ---------------------------------------------------------------------------------------
etapa "2. Usuário todonotes e diretórios"

if ! id todonotes >/dev/null 2>&1; then
  adduser --system --group --home "$BASE" --shell /usr/sbin/nologin todonotes >/dev/null
  ok "usuário todonotes criado"
else
  ok "usuário todonotes já existia"
fi

# 755 em /opt/todonotes: o código é público, e o postgres precisa ler o criar-usuario.sql
# daqui. O que é sensível tem permissão própria: o jar (640) e a trilha de auditoria (750).
install -d -m 755 -o todonotes -g todonotes "$BASE"
install -d -m 750 -o todonotes -g todonotes "$BASE/logs"
install -d -m 755 -o todonotes -g todonotes "$SITE" "$SITE/releases"
install -d -m 700 -o postgres -g postgres /var/lib/postgresql/redeploy
ok "$BASE, $BASE/logs, $SITE/releases, /var/lib/postgresql/redeploy"

# ---------------------------------------------------------------------------------------
etapa "3. Código"

clonar_ou_atualizar() {
  local repositorio=$1 destino=$2
  if [ -d "$destino/.git" ]; then
    como_todonotes git -C "$destino" fetch --quiet origin
    ok "$destino: fetch feito, HEAD em $(como_todonotes git -C "$destino" log -1 --format=%h)"
  else
    como_todonotes git clone --quiet "$repositorio" "$destino"
    ok "$destino: clonado em $(como_todonotes git -C "$destino" log -1 --format=%h)"
  fi
}
clonar_ou_atualizar "$REPO_API" "$BASE/backend"
clonar_ou_atualizar "$REPO_FRONTEND" "$BASE/frontend"

DEPLOY="$BASE/backend/deploy"
for arquivo in todonotes.service nginx-todonotes.conf sudoers-todonotes-deploy; do
  [ -f "$DEPLOY/$arquivo" ] || falha "$DEPLOY/$arquivo não existe — o push da main foi feito?"
done

# ---------------------------------------------------------------------------------------
etapa "4. Segredos"

install -d -m 700 -o root -g root "$ENV_DIR"

role_existe() {
  [ "$(psql_postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$1'")" = 1 ]
}

ENV_NOVO=0
if [ ! -f "$ENV_FILE" ]; then
  if role_existe todonotes_app || role_existe todonotes_migracao; then
    falha "as roles todonotes_* já existem, mas $ENV_FILE não: as senhas delas são desconhecidas. Apague as roles ou escreva o arquivo à mão."
  fi

  # Hex para as senhas de banco: nada que precise de escape no SQL nem no arquivo de
  # ambiente. Base64 de 48 bytes para o JWT, o mínimo do HS384 — e 48 é múltiplo de 3,
  # então não há `=` de preenchimento.
  (
    umask 077
    cat > "$ENV_FILE" <<EOF
# Gerado por preparar-servidor.sh em $(date -u +%Y-%m-%dT%H:%MZ). Modelo comentado em
# $DEPLOY/todonotes.env.modelo.
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=$PORTA_API
MANAGEMENT_PORT=$PORTA_MONITORAMENTO
SERVER_ADDRESS=127.0.0.1
FORWARD_HEADERS_STRATEGY=FRAMEWORK
DB_URL=jdbc:postgresql://localhost:5432/todonotes_db
DB_USERNAME=todonotes_app
DB_PASSWORD=$(openssl rand -hex 24)
DB_MIGRACAO_USERNAME=todonotes_migracao
DB_MIGRACAO_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
CORS_ORIGENS=
EOF
  )
  ENV_NOVO=1
  ok "$ENV_FILE escrito com três segredos novos"
else
  ok "$ENV_FILE já existia — mantido como está"
fi
chown root:root "$ENV_FILE"
chmod 600 "$ENV_FILE"

# ---------------------------------------------------------------------------------------
etapa "5. Banco"

if [ "$(psql_postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'todonotes_db'")" = 1 ]; then
  ok "todonotes_db já existia"
else
  psql_postgres -qc "CREATE DATABASE todonotes_db"
  ok "todonotes_db criado"
fi

if [ "$ENV_NOVO" = 1 ]; then
  # As senhas entram por variável de ambiente do awk, e não por argumento: argumento
  # aparece em `ps aux` para qualquer usuário da máquina; ambiente, só para root e o dono.
  # O script preenchido vai pela entrada padrão e nunca toca o disco.
  SENHA_APP=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-) \
  SENHA_MIG=$(grep '^DB_MIGRACAO_PASSWORD=' "$ENV_FILE" | cut -d= -f2-) \
  awk '{ gsub(/SENHA_DA_APLICACAO/, ENVIRON["SENHA_APP"]); gsub(/SENHA_DA_MIGRACAO/, ENVIRON["SENHA_MIG"]); print }' \
    "$BASE/backend/db/criar-roles.sql" \
    | psql_postgres -q -d todonotes_db -f -
  ok "roles todonotes_migracao e todonotes_app criadas (a tabela acima deve ter as duas, rolsuper = f)"
else
  role_existe todonotes_app && role_existe todonotes_migracao \
    || falha "o $ENV_FILE existe, mas falta uma das roles todonotes_*"
  ok "roles já existiam"
fi

# ---------------------------------------------------------------------------------------
etapa "6. systemd"

install -m 644 -o root -g root "$DEPLOY/todonotes.service" /etc/systemd/system/todonotes.service
systemctl daemon-reload
systemctl enable --quiet todonotes
ok "todonotes.service instalado e habilitado no boot — não iniciado"

# ---------------------------------------------------------------------------------------
etapa "7. nginx"

if [ -f "$NGINX_SITE" ]; then
  ok "$NGINX_SITE já existia — mantido (o certbot pode ter escrito nele)"
else
  sed "s/todo-notes\.keystech\.dev/$DOMINIO/g" "$DEPLOY/nginx-todonotes.conf" > "$NGINX_SITE"
  chmod 644 "$NGINX_SITE"
  ok "$NGINX_SITE escrito para $DOMINIO"
fi
ln -sfn "$NGINX_SITE" /etc/nginx/sites-enabled/todonotes

# Uma configuração inválida derrubaria o GeFinX e a oficina junto: o reload só vem
# depois do -t aprovar.
nginx -t
systemctl reload nginx
ok "nginx recarregado"

# ---------------------------------------------------------------------------------------
etapa "8. sudo do claude-todonotes"

regra_temporaria=$(mktemp)
cp "$DEPLOY/sudoers-todonotes-deploy" "$regra_temporaria"
visudo -cqf "$regra_temporaria" || { rm -f "$regra_temporaria"; falha "a regra de sudo não passou no visudo"; }
install -m 440 -o root -g root "$regra_temporaria" /etc/sudoers.d/todonotes-deploy
rm -f "$regra_temporaria"
visudo -cq || falha "o sudoers inteiro deixou de validar — confira /etc/sudoers.d/todonotes-deploy"
ok "/etc/sudoers.d/todonotes-deploy instalado"

sudo -l -U claude-todonotes

# ---------------------------------------------------------------------------------------
etapa "Pronto"

cat <<EOF
   Próximos passos, nesta ordem:

   1. DNS: registro A de "${DOMINIO%%.*}" apontando para este servidor, DNS-only (nuvem cinza).
   2. Quando resolver:  certbot --nginx -d $DOMINIO   (escolha redirecionar para HTTPS)
   3. Avise o Claude Code: o build, a subida e a publicação são com ele, via claude-todonotes.
EOF
free -h
