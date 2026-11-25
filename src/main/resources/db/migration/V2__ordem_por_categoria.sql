-- Segunda ordem: o modo de visualizacao por categoria.
--
-- A lista passa a ter dois agrupamentos -- por prioridade e por categoria --, e cada um
-- guarda a sua propria ordem: arrastar num modo nao mexe no outro. Duas ordens sao dois
-- dados, e e por isso que esta migration acrescenta uma coluna em vez de reinterpretar a
-- que existe.

-- ---------------------------------------------------------------------------
-- 1. A coluna que existe passa a dizer de qual ordem ela e.
--
-- Com duas ordens na mesma tabela, um "posicao" solto obriga quem le a adivinhar de que
-- agrupamento ele fala -- e a adivinhar errado em metade das vezes. O JSON da API continua
-- mandando "posicao", significando o indice no agrupamento que foi pedido; a mudanca e
-- interna, e para que o codigo possa nomear as duas.
-- ---------------------------------------------------------------------------
ALTER TABLE tarefas RENAME COLUMN posicao TO posicao_na_prioridade;

ALTER TABLE tarefas RENAME CONSTRAINT ck_tarefas_posicao TO ck_tarefas_posicao_na_prioridade;
ALTER TABLE tarefas RENAME CONSTRAINT uq_tarefas_ordem   TO uq_tarefas_ordem_na_prioridade;

-- ---------------------------------------------------------------------------
-- 2. A ordem dentro do grupo de categoria.
--
-- Nasce anulavel para o preenchimento abaixo caber numa migration so, e termina NOT NULL:
-- toda tarefa pertence a exatamente um grupo de categoria, inclusive ao grupo das que nao
-- tem etiqueta.
-- ---------------------------------------------------------------------------
ALTER TABLE tarefas ADD COLUMN posicao_na_categoria INTEGER;

-- A ordem inicial e a que a pessoa ja ve hoje: dentro de cada grupo, os cards entram na
-- ordem das faixas, da prioridade mais alta para a mais baixa. Comecar com tudo em zero
-- deixaria a primeira pintura do modo novo em ordem de id, que nao quer dizer nada.
--
-- O PARTITION BY trata os categoria_id nulos como iguais, e por isso as tarefas sem
-- etiqueta saem numeradas como um grupo unico -- a mesma leitura do NULLS NOT DISTINCT
-- logo abaixo.
UPDATE tarefas AS t
SET posicao_na_categoria = numeradas.ordem - 1
FROM (
    SELECT id,
           row_number() OVER (
               PARTITION BY usuario_id, categoria_id
               ORDER BY prioridade, posicao_na_prioridade
           ) AS ordem
      FROM tarefas
) AS numeradas
WHERE t.id = numeradas.id;

ALTER TABLE tarefas ALTER COLUMN posicao_na_categoria SET NOT NULL;

ALTER TABLE tarefas
    ADD CONSTRAINT ck_tarefas_posicao_na_categoria CHECK (posicao_na_categoria >= 0);

-- ---------------------------------------------------------------------------
-- 3. A unicidade da ordem nova -- e o NULLS NOT DISTINCT, que e o centro desta migration.
--
-- categoria_id e anulavel: tarefa sem etiqueta e estado normal, e e o que sobra quando a
-- categoria e excluida. Numa UNIQUE comum o PostgreSQL trata cada nulo como distinto de
-- todos os outros, entao as tarefas sem etiqueta ficariam FORA da restricao: o grupo "Sem
-- categoria" seria o unico sem garantia de ordem, justamente o que recebe os orfaos de toda
-- exclusao de categoria. O NULLS NOT DISTINCT (PostgreSQL 15+) faz dos nulos um valor como
-- qualquer outro para efeito de unicidade, e com isso o grupo passa a ser um grupo de
-- verdade.
--
-- Deferida pelo mesmo motivo da ordem da faixa: renumerar um grupo passa por um estado em
-- que duas linhas disputam o mesmo lugar, e a conferencia no commit e o que permite esse
-- meio do caminho.
--
-- E tambem o indice da listagem agrupada por categoria -- "WHERE usuario_id = ? ORDER BY
-- categoria_id, posicao_na_categoria" sai dele na mesma passagem, e nenhum indice extra
-- existe, como na ordem da faixa.
--
-- A consequencia que o codigo tem de honrar: excluir uma categoria joga as tarefas dela no
-- grupo das sem etiqueta pelo ON DELETE SET NULL, com posicoes que ja existem lah. Sem
-- renumerar os dois grupos na mesma transacao, esta restricao aborta o DELETE no commit. O
-- CategoriaService e quem faz isso.
-- ---------------------------------------------------------------------------
ALTER TABLE tarefas
    ADD CONSTRAINT uq_tarefas_ordem_na_categoria
    UNIQUE NULLS NOT DISTINCT (usuario_id, categoria_id, posicao_na_categoria)
    DEFERRABLE INITIALLY DEFERRED;
