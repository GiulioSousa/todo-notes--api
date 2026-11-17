-- Cria a conta de acesso ao todo-notes.
--
-- COMO RODAR
--
--   psql -h localhost -p 5433 -U postgres -d todonotes_db \
--        -v usuario=giulivan -v senha='uma frase de senha longa' -f db/criar-usuario.sql
--
-- A senha vai na linha de comando e fica no historico do shell. Para evitar isso, abra o
-- psql e informe-a na hora:
--
--   psql -h localhost -p 5433 -U postgres -d todonotes_db
--   \set usuario giulivan
--   \prompt 'Senha: ' senha
--   \i db/criar-usuario.sql
--
-- O `\prompt` mostra o que e digitado na tela — ele tira a senha do historico, nao dos
-- olhos de quem estiver por perto.
--
-- POR QUE ISTO EXISTE
--
-- Nao ha rota de cadastro, por decisao (Decisao 9 do ESPECIFICACOES.md): sem verificacao
-- de e-mail, uma rota aberta deixaria qualquer um que alcancasse o endereco criar conta.
-- A conta nasce aqui, por quem opera o banco.
--
-- Nao e uma migration do Flyway: migration descreve schema, e isto e dado — roda uma vez
-- por instalacao, com valores diferentes em cada uma.
--
-- POR QUE NAO HA SEMENTE
--
-- O script do GeFinX cria categorias e conta padrao junto com o usuario, porque la toda
-- transacao exige uma conta e a primeira tela viria inutil. Aqui categoria e opcional na
-- tarefa e se cria de dentro do proprio formulario, e preferencias tem padrao no dominio
-- (`Preferencias.padrao()`), sem precisar de linha no banco. Entao a conta nova nasce so
-- com ela mesma, e a primeira tela ja e utilizavel.
--
-- SOBRE O HASH
--
-- O pgcrypto gera BCrypt no mesmo formato que o BCryptPasswordEncoder do Spring le
-- (`$2a$10$...`), entao a senha criada aqui vale no login sem passo intermediario. Criar a
-- extensao exige superusuario, e e por isso que o script roda como `postgres`, nao como
-- `todonotes_app`.
--
-- O custo do `gen_salt('bf', N)` precisa acompanhar o do BCryptPasswordEncoder no
-- SecurityConfig. Divergir nao impede ninguem de entrar — o `matches` le o custo de dentro
-- do hash —, mas derruba a defesa contra enumeracao de contas: o caso de uso de login
-- confere um hash descartavel quando a conta nao existe, para que a resposta demore o
-- mesmo tanto, e um custo menor aqui tornaria a conta inexistente mais rapida que a real.
--
-- A senha em texto puro passa pela conexao e pode aparecer no log do servidor, se ele
-- estiver com `log_statement = all`. Num banco pessoal e aceitavel; conferir antes num
-- banco compartilhado — e esta instancia e compartilhada com o GeFinX.

\set ON_ERROR_STOP on

-- A senha pode ter acentos, e no Windows o cliente costuma assumir WIN1252. Sem esta
-- linha, a senha gravada seria o hash de bytes diferentes dos que voce digitou no login.
\encoding UTF8

-- ---------------------------------------------------------------------------
-- Conferencias antes de escrever. Todas param o script com uma mensagem, em vez de
-- deixarem o erro estourar do meio do INSERT.
-- ---------------------------------------------------------------------------

\if :{?usuario}
\else
  \echo 'ERRO: defina o nome de usuario. Ex.: -v usuario=giulivan'
  \quit
\endif

\if :{?senha}
\else
  \echo 'ERRO: defina a senha. Ex.: -v senha=... ou \\prompt ''Senha: '' senha'
  \quit
\endif

-- A forma canonica e a que o login procura: minuscula e sem espacos nas pontas. O script
-- normaliza em vez de recusar, para que `-v usuario=Giulivan` nao vire uma conta que nao
-- abre. A mesma regra vive na CHECK da tabela usuarios.
SELECT lower(btrim(:'usuario')) AS usuario_canonico \gset

SELECT length(:'usuario_canonico') BETWEEN 3 AND 60 AS nome_cabe \gset
\if :nome_cabe
\else
  \echo 'ERRO: o nome de usuario precisa ter de 3 a 60 caracteres.'
  \quit
\endif

SELECT NOT EXISTS (SELECT 1 FROM usuarios WHERE usuario = :'usuario_canonico') AS nome_livre \gset
\if :nome_livre
\else
  \echo 'ERRO: ja existe uma conta com esse nome de usuario.'
  \quit
\endif

-- Dez caracteres e o piso, o mesmo do GeFinX. O teto e do proprio BCrypt, que nao olha
-- alem do 72o byte — e o limite e em BYTES, o que pesa em portugues, onde cada acento
-- ocupa dois. Sem esta conferencia, o excedente seria truncado em silencio e a senha
-- efetiva seria mais curta do que a escolhida.
SELECT length(:'senha') >= 10 AND octet_length(:'senha') <= 72 AS senha_ok \gset
\if :senha_ok
\else
  \echo 'ERRO: a senha precisa ter no minimo 10 caracteres e no maximo 72 bytes (acentos contam dois).'
  \quit
\endif

-- ---------------------------------------------------------------------------
-- A escrita. Uma instrucao so, e por isso sem BEGIN/COMMIT explicito: o GeFinX precisa da
-- transacao porque escreve em tres tabelas, e aqui nao ha semente para ficar pela metade.
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- `criado_em` fica de fora: a coluna tem DEFAULT now().
INSERT INTO usuarios (usuario, senha_hash)
VALUES (:'usuario_canonico', crypt(:'senha', gen_salt('bf', 10)));

-- ---------------------------------------------------------------------------
-- Conferencia. Esperado: a conta nova, com o hash comecando em `$2a$10$`.
-- ---------------------------------------------------------------------------
SELECT id, usuario, criado_em, left(senha_hash, 7) AS prefixo_do_hash
  FROM usuarios
 WHERE usuario = :'usuario_canonico';

\echo 'Conta criada. Entre no todo-notes com o nome de usuario e a senha informados.'
