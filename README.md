# todo-notes — API

API do todo-notes: tarefas em três faixas de prioridade, arrastáveis, com categorias e
preferências de exibição. Conta única por instalação, criada pelo banco — não há rota de
cadastro.

Spring Boot com arquitetura em quatro camadas por contexto (`dominio`, `aplicacao`,
`infraestrutura`, `interfaces/web`), dois contextos (`usuarios` e `tarefas`) e o
`compartilhado` para segurança, auditoria e tratamento de erro. O esquema é do Flyway, e o
`ddl-auto: validate` recusa subir se entidade e tabela divergirem.

O frontend fica em [todo-notes--frontend](https://github.com/GiulioSousa/todo-notes--frontend),
com linha de versão própria. Na máquina de desenvolvimento este repositório é a pasta
`backend/`, ao lado dele.

## Pilha

<!-- versoes:inicio -->

| Peça | Versão |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.2 |
| JJWT | 0.13.0 |
| Bucket4j | 8.22.0 |

<!-- versoes:fim -->

PostgreSQL 18.3 em desenvolvimento. O `ON DELETE SET NULL` da categoria exige 15 ou mais.

A tabela acima é escrita pelo `.github/scripts/versoes-readme.mjs`, que a lê do `pom.xml` —
não edite à mão. O CI confere com `--conferir`.

## Primeira subida

A ordem importa, e errá-la custa tempo. O roteiro completo, com as explicações, está em
[`src/main/resources/application-example.yml`](src/main/resources/application-example.yml).
Em resumo:

1. Criar o banco, como superusuário:
   `psql -U postgres -c "CREATE DATABASE todonotes_db"`
2. Criar as duas roles, trabalhando sobre uma **cópia** do script versionado:
   `cp db/criar-roles.sql db/criar-roles.local.sql`, trocar os dois placeholders de senha e
   rodar a cópia. O `.gitignore` cobre `db/*.local.sql`.
3. Copiar `application-example.yml` para `application-local.yml` e preencher as duas senhas e
   a chave do JWT — 48 bytes em Base64, exigência do HS384.
4. `mvn spring-boot:run`. É o Flyway, na subida, que cria as tabelas.
5. Só agora a conta de acesso, com `db/criar-usuario.sql` — a tabela `usuarios` passou a
   existir no passo 4.

Nenhum desses arquivos com senha é versionado: a convenção do projeto é que **"local" no nome
significa "contém segredo"**, e o `.gitignore` cobre `*.local.*` e `*-local.*`.

## Testes

```
mvn test
```

**Precisa do banco no ar.** Não há Testcontainers nem perfil de teste: três das oito classes
sobem o contexto contra o PostgreSQL de desenvolvimento, com o mesmo `application-local.yml`.
É decisão de projeto — testar contra o banco de verdade vale mais do que contra um substituto
—, e é o que o CI reproduz criando o banco e as roles a cada execução.

As contas que os testes usam nascem e morrem em cada cenário, por `INSERT`, do mesmo jeito que
nascem em produção.

## Versões

Versão deste repositório:

<!-- x-release-please-start-version -->
0.1.0
<!-- x-release-please-end -->

Quem escreve essa linha, a versão do `pom.xml`, as tags e o `CHANGELOG.md` é o
`release-please`, a partir das mensagens de commit — nunca a mão. O primeiro merge do PR de
release cria a `v0.1.0`.

Antes do 1.0.0 não existe patch: `feat` e `fix` sobem a minor, e a release não sai a cada
`feat` — o PR fica aberto acumulando até haver mudança de impacto.

## Commits

Conventional Commits, descrição em português, imperativo presente, minúscula, sem ponto
final. **Assunto só em ASCII**; o corpo pode ter acento. Escopo é bounded context, não
camada, e a lista está em
[`.github/scripts/escopos-de-commit.txt`](.github/scripts/escopos-de-commit.txt).

Commit fora da convenção não quebra nada — ele só desaparece do CHANGELOG, sem aviso. Para
que isso não passe batido:

```
.github/scripts/conferir-commits.sh
```

É o mesmo script que o CI roda, e nele não há linha de coautoria: o histórico diz o que mudou,
e a autoria do repositório é de uma pessoa só.

## Segredos

A convenção é uma só: **"local" no nome significa "contém segredo"**, e o `.gitignore` cobre
`*.local.*`, `*-local.*`, `db/*.local.sql` e os despejos da JVM, que carregam o ambiente
inteiro do processo. Para conferir que nada escapou:

```
.github/scripts/conferir-segredos.sh
```

Ele acusa duas coisas: arquivo rastreado que o próprio `.gitignore` diz ignorar — o caso de
quem entrou antes de a regra existir, e que o Git versiona em silêncio para sempre — e nome com
forma de segredo ainda que o `.gitignore` não o cubra. O segundo não consulta o `.gitignore` de
propósito: o furo que esta conferência existe para pegar já aconteceu **dentro** dele.

O CI roda esse script e, depois, o `gitleaks` por conteúdo e entropia no histórico inteiro. Os
placeholders versionados estão permitidos por valor no `.gitleaks.toml`, nunca por caminho.

## Deploy

A aplicação roda atrás do nginx, que serve o frontend e repassa `/api` para ela, sob um só
domínio. Os arquivos do servidor estão em [`deploy/`](deploy/), sem segredo:

| Arquivo | Destino no servidor |
|---|---|
| `preparar-servidor.sh` | Roda uma vez, como root: usuário, diretórios, segredos, banco, unit, nginx e sudo |
| `todonotes.service` | `/etc/systemd/system/todonotes.service` |
| `nginx-todonotes.conf` | `/etc/nginx/sites-available/todonotes` (só HTTP; o certbot acrescenta o TLS) |
| `sudoers-todonotes-deploy` | `/etc/sudoers.d/todonotes-deploy` |
| `todonotes.env.modelo` | Modelo comentado do `/etc/todonotes/todonotes.env`, que o script gera |

Três configurações de produção andam **juntas**, e todas vêm do arquivo de ambiente:

- `SERVER_ADDRESS=127.0.0.1` — o nginx é a única porta de entrada;
- `FORWARD_HEADERS_STRATEGY=FRAMEWORK` — o limite de login volta a ver o endereço real do
  cliente. Sem ela, atrás do proxy todos os baldes colapsam num só, e o primeiro que errasse
  cinco senhas trancaria o login de todo mundo. Sem o loopback acima, ao contrário, o cliente
  forjaria o próprio `X-Forwarded-For`;
- o Actuator na `MANAGEMENT_PORT`, ligada a `127.0.0.1`, só com `health` e sem detalhes.

O nginx precisa **sobrescrever** o `X-Forwarded-For` com `$remote_addr`. Com
`$proxy_add_x_forwarded_for`, o valor do cliente iria na frente, e o Spring toma a primeira
entrada.

O jar não leva o `application-local.yml`: o `maven-jar-plugin` exclui `*-local.*` e `*.local.*`
do empacotamento. Confira mesmo assim antes de publicar:

```bash
unzip -l target/todonotes-backend.jar | grep application-
```

Só `application.yml` e `application-example.yml` podem aparecer.

A especificação do projeto e o diário de execução ficam fora dos repositórios, por decisão:
descrevem o projeto inteiro e não pertencem nem ao backend nem ao frontend.
