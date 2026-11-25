-- Confere, executando, as construcoes da V2 que o compilador nao alcanca: a numeracao
-- inicial do grupo de categoria, o NULLS NOT DISTINCT, a restricao deferida do grupo e a
-- consequencia que o codigo tem de honrar na exclusao de categoria.
--
-- Roda a MIGRATION DE VERDADE, com \i, em vez de repetir os comandos dela: o que este
-- script confere e o arquivo que o Flyway vai aplicar, nao uma copia que pode divergir.
--
--   cd backend
--   psql -h localhost -p 5433 -U todonotes_migracao -d todonotes_db -f db/conferir-v2.sql
--
-- Roda inteiro dentro de uma transacao e termina com ROLLBACK: a V2 NAO fica aplicada, e
-- nenhuma linha fica para tras. Pode rodar num banco que ja tenha dados -- o UPDATE de
-- preenchimento da migration toca as tarefas que existirem, e o ROLLBACK as devolve como
-- estavam.
--
-- Tres conferencias esperam ERRO do banco, e dizem isso antes. Nelas, a mensagem de erro
-- do PostgreSQL e a prova de que a regra existe.

-- Sem `\encoding UTF8` de proposito, pelo mesmo motivo do conferir-v1.sql: forcar UTF-8 no
-- cliente embaralha as mensagens de erro do PostgreSQL num console Windows em CP850 --
-- justamente as mensagens que sao a prova das conferencias 3, 5 e 6.
\set ON_ERROR_STOP on

BEGIN;

-- Dados de apoio, ainda com o nome antigo da coluna: a V2 so e aplicada depois deles, o que
-- faz desta parte do script tambem uma conferencia do RENAME.
INSERT INTO usuarios (usuario, senha_hash)
VALUES ('conferencia-v2', 'nao-e-hash-de-verdade')
RETURNING id AS usuario_id \gset

INSERT INTO categorias (nome, usuario_id)
VALUES ('Casa', :usuario_id)
RETURNING id AS categoria_id \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('A - alta 0, Casa', 'PENDENTE', 'ALTA', 0, :categoria_id, :usuario_id)
RETURNING id AS tarefa_a \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('B - alta 1, sem categoria', 'PENDENTE', 'ALTA', 1, NULL, :usuario_id)
RETURNING id AS tarefa_b \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('C - media 0, Casa', 'PENDENTE', 'MEDIA', 0, :categoria_id, :usuario_id)
RETURNING id AS tarefa_c \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('D - baixa 0, sem categoria', 'PENDENTE', 'BAIXA', 0, NULL, :usuario_id)
RETURNING id AS tarefa_d \gset


\echo ''
\echo '== aplicando a V2 =================================================='

\i src/main/resources/db/migration/V2__ordem_por_categoria.sql


\echo ''
\echo '== 1. os nomes novos ==============================================='
\echo 'Esperado: as duas colunas de posicao e as quatro restricoes com nome do agrupamento.'

SELECT column_name
  FROM information_schema.columns
 WHERE table_name = 'tarefas' AND column_name LIKE 'posicao%'
 ORDER BY column_name;

SELECT conname
  FROM pg_constraint
 WHERE conrelid = 'tarefas'::regclass
   AND (conname LIKE '%posicao%' OR conname LIKE '%ordem%')
 ORDER BY conname;


\echo ''
\echo '== 2. numeracao inicial dos grupos ================================='
\echo 'Esperado: dentro de cada grupo, a ordem das faixas -- ALTA antes de MEDIA antes de'
\echo 'BAIXA --, numerada de 0. Casa: A=0, C=1. Sem categoria: B=0, D=1.'

SELECT coalesce(c.nome, '(sem categoria)') AS grupo,
       t.posicao_na_categoria,
       t.prioridade,
       t.titulo
  FROM tarefas t
  LEFT JOIN categorias c ON c.id = t.categoria_id
 WHERE t.usuario_id = :usuario_id
 ORDER BY grupo, t.posicao_na_categoria;


\echo ''
\echo '== 3. NULLS NOT DISTINCT ==========================================='
\echo 'Esperado: ERRO de chave duplicada. Duas tarefas SEM categoria na mesma posicao do'
\echo 'grupo. Numa UNIQUE comum este INSERT passaria, porque o PostgreSQL trata cada nulo'
\echo 'como distinto dos outros -- e o grupo "Sem categoria" ficaria sem ordem garantida.'
\set ON_ERROR_STOP off

SAVEPOINT antes_do_nulo;
INSERT INTO tarefas (titulo, status, prioridade, posicao_na_prioridade, posicao_na_categoria, categoria_id, usuario_id)
VALUES ('E - sem categoria, posicao 0 de novo', 'PENDENTE', 'BAIXA', 1, 0, NULL, :usuario_id);
SET CONSTRAINTS ALL IMMEDIATE;
ROLLBACK TO SAVEPOINT antes_do_nulo;

\set ON_ERROR_STOP on
SET CONSTRAINTS ALL DEFERRED;


\echo ''
\echo '== 4. restricao deferida: trocar duas posicoes no grupo ============'
\echo 'A primeira UPDATE deixa A e C na posicao 1 do grupo Casa. Sem DEFERRABLE, ela'
\echo 'falharia aqui. Esperado: C=0 e A=1, sem erro.'

UPDATE tarefas SET posicao_na_categoria = 1 WHERE id = :tarefa_a;
UPDATE tarefas SET posicao_na_categoria = 0 WHERE id = :tarefa_c;
SET CONSTRAINTS ALL IMMEDIATE;

SELECT posicao_na_categoria, titulo
  FROM tarefas
 WHERE id IN (:tarefa_a, :tarefa_c)
 ORDER BY posicao_na_categoria;

SET CONSTRAINTS ALL DEFERRED;


\echo ''
\echo '== 5. excluir categoria SEM renumerar =============================='
\echo 'Esperado: ERRO. O ON DELETE SET NULL joga A e C no grupo das sem categoria com as'
\echo 'posicoes 1 e 0, que B e D ja ocupam. E a razao de o CategoriaService ter de renumerar'
\echo 'os dois grupos na mesma transacao -- esta conferencia e o teste daquela regra.'
\set ON_ERROR_STOP off

SAVEPOINT antes_da_exclusao_crua;
DELETE FROM categorias WHERE id = :categoria_id;
SET CONSTRAINTS ALL IMMEDIATE;
ROLLBACK TO SAVEPOINT antes_da_exclusao_crua;

\set ON_ERROR_STOP on
SET CONSTRAINTS ALL DEFERRED;


\echo ''
\echo '== 6. excluir categoria RENUMERANDO ================================'
\echo 'O mesmo DELETE, agora com os orfaos postos no fim do grupo de destino -- o que o'
\echo 'codigo vai fazer. Esperado: sem erro, e as quatro tarefas em 0, 1, 2, 3.'

DELETE FROM categorias WHERE id = :categoria_id;
UPDATE tarefas SET posicao_na_categoria = 2 WHERE id = :tarefa_c;
UPDATE tarefas SET posicao_na_categoria = 3 WHERE id = :tarefa_a;
SET CONSTRAINTS ALL IMMEDIATE;

SELECT posicao_na_categoria, categoria_id IS NULL AS desetiquetada, titulo
  FROM tarefas
 WHERE usuario_id = :usuario_id
 ORDER BY posicao_na_categoria;


\echo ''
\echo '== fim: desfazendo tudo, inclusive a V2 ============================'
ROLLBACK;

\echo 'Esperado: zero nas duas. Se vier 1, o ROLLBACK nao aconteceu.'

SELECT count(*) AS linhas_de_conferencia_que_sobraram
  FROM usuarios
 WHERE usuario = 'conferencia-v2';

SELECT count(*) AS colunas_da_v2_que_ficaram
  FROM information_schema.columns
 WHERE table_name = 'tarefas' AND column_name = 'posicao_na_categoria';
