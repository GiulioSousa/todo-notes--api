-- Confere, executando, as construcoes da V1 que o compilador nao alcanca e que so falham
-- em tempo de execucao: a ordem do ENUM nativo, o indice unico sem diferenciar caixa, a
-- restricao unica deferida, a CHECK de conclusao e o ON DELETE SET NULL por coluna.
--
-- Nao confere o esquema: quem confere e o proprio Flyway ao rodar a V1. Este script
-- confere o COMPORTAMENTO do que a V1 criou.
--
--   psql -h localhost -p 5433 -U postgres -d todonotes_v1_teste -f db/conferir-v1.sql
--
-- Roda inteiro dentro de uma transacao e termina com ROLLBACK: nao deixa linha nenhuma,
-- e pode ser repetido quantas vezes quiser, inclusive num banco que ja tenha dados.
--
-- Duas conferencias esperam ERRO do banco, e dizem isso antes. Nessas duas, a mensagem de
-- erro do PostgreSQL e a prova de que a regra existe.

-- Sem `\encoding UTF8` de proposito, ao contrario do criar-usuario.sql: este script nao
-- insere texto acentuado, e forcar UTF-8 no cliente embaralha as mensagens de erro do
-- PostgreSQL num console Windows em CP850 — justamente as mensagens que sao a prova das
-- conferencias 2 e 4.
\set ON_ERROR_STOP on

BEGIN;

-- Dados de apoio. Os ids vem do banco, e nao fixos, para o script conviver com o que ja
-- existir na tabela.
INSERT INTO usuarios (usuario, senha_hash)
VALUES ('conferencia-v1', 'nao-e-hash-de-verdade')
RETURNING id AS usuario_id \gset

INSERT INTO categorias (nome, usuario_id)
VALUES ('Casa', :usuario_id)
RETURNING id AS categoria_id \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('A - alta, posicao 0', 'PENDENTE', 'ALTA', 0, :categoria_id, :usuario_id)
RETURNING id AS tarefa_a \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('B - alta, posicao 1', 'PENDENTE', 'ALTA', 1, NULL, :usuario_id)
RETURNING id AS tarefa_b \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('C - media, posicao 0', 'PENDENTE', 'MEDIA', 0, NULL, :usuario_id)
RETURNING id AS tarefa_c \gset

INSERT INTO tarefas (titulo, status, prioridade, posicao, categoria_id, usuario_id)
VALUES ('D - baixa, posicao 0', 'PENDENTE', 'BAIXA', 0, NULL, :usuario_id)
RETURNING id AS tarefa_d \gset


\echo ''
\echo '== 1. ordem do ENUM nativo =========================================='
\echo 'Esperado: ALTA, ALTA, MEDIA, BAIXA. Alfabetico seria ALTA, ALTA, BAIXA, MEDIA.'

SELECT prioridade, posicao, titulo
  FROM tarefas
 WHERE usuario_id = :usuario_id
 ORDER BY prioridade, posicao;


\echo ''
\echo '== 2. nome de categoria sem diferenciar caixa ======================='
\echo 'Esperado: ERRO de chave duplicada ao inserir "casa" com "Casa" cadastrada.'
\set ON_ERROR_STOP off

SAVEPOINT antes_da_caixa;
INSERT INTO categorias (nome, usuario_id) VALUES ('casa', :usuario_id);
ROLLBACK TO SAVEPOINT antes_da_caixa;

\set ON_ERROR_STOP on


\echo ''
\echo '== 3. restricao unica deferida: trocar duas posicoes ================'
\echo 'A primeira UPDATE deixa duas tarefas na posicao 1. Sem DEFERRABLE, ela falharia'
\echo 'aqui. O SET CONSTRAINTS ALL IMMEDIATE cobra a conferencia antes do fim, e passa'
\echo 'porque o estado final e consistente.'

UPDATE tarefas SET posicao = 1 WHERE id = :tarefa_a;
UPDATE tarefas SET posicao = 0 WHERE id = :tarefa_b;
SET CONSTRAINTS ALL IMMEDIATE;

SELECT posicao, titulo
  FROM tarefas
 WHERE id IN (:tarefa_a, :tarefa_b)
 ORDER BY posicao;


\echo ''
\echo '== 4. CHECK de conclusao ============================================'
\echo 'Esperado: ERRO. Status CONCLUIDA sem data de conclusao nao pode existir.'
\set ON_ERROR_STOP off

SAVEPOINT antes_da_conclusao;
INSERT INTO tarefas (titulo, status, prioridade, posicao, usuario_id)
VALUES ('E - concluida sem data', 'CONCLUIDA', 'BAIXA', 1, :usuario_id);
ROLLBACK TO SAVEPOINT antes_da_conclusao;

\echo ''
\echo 'E o contrario tambem: PENDENTE com data de conclusao. Esperado: ERRO.'

SAVEPOINT antes_da_pendente;
INSERT INTO tarefas (titulo, status, prioridade, posicao, usuario_id, data_conclusao)
VALUES ('F - pendente com data', 'PENDENTE', 'BAIXA', 1, :usuario_id, now());
ROLLBACK TO SAVEPOINT antes_da_pendente;

\set ON_ERROR_STOP on


\echo ''
\echo '== 5. ON DELETE SET NULL (categoria_id) ============================='
\echo 'Excluir a categoria desetiqueta a tarefa A e nao toca no dono dela.'
\echo 'Esperado: uma linha, com desetiquetada = t e dono_intacto = t.'

DELETE FROM categorias WHERE id = :categoria_id;

SELECT titulo,
       categoria_id IS NULL        AS desetiquetada,
       usuario_id = :usuario_id    AS dono_intacto
  FROM tarefas
 WHERE id = :tarefa_a;


\echo ''
\echo '== fim: desfazendo tudo ============================================='
ROLLBACK;

\echo 'Esperado: zero. Se vier 1, o ROLLBACK nao aconteceu.'

SELECT count(*) AS linhas_de_conferencia_que_sobraram
  FROM usuarios
 WHERE usuario = 'conferencia-v1';
