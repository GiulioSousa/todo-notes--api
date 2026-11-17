-- Esquema inicial: tarefas com faixa de prioridade arrastavel, categorias e preferencias.

CREATE TYPE prioridade_tarefa AS ENUM ('ALTA', 'MEDIA', 'BAIXA');

CREATE TABLE usuarios (
    id          BIGSERIAL    PRIMARY KEY,
    usuario     VARCHAR(60)  NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_usuarios_usuario            UNIQUE (usuario),
    CONSTRAINT ck_usuarios_usuario_normalizado CHECK (usuario = lower(btrim(usuario))),
    CONSTRAINT ck_usuarios_usuario_tamanho     CHECK (length(usuario) BETWEEN 3 AND 60)
);

CREATE TABLE categorias (
    id          BIGSERIAL   PRIMARY KEY,
    nome        VARCHAR(40) NOT NULL,
    usuario_id  BIGINT      NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_categorias_usuario    FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT uq_categorias_id_usuario UNIQUE (id, usuario_id),
    CONSTRAINT ck_categorias_nome       CHECK (nome = btrim(nome) AND nome <> '')
);

-- Unicidade do nome sem diferenciar maiuscula de minuscula; a grafia digitada e a que fica.
CREATE UNIQUE INDEX uq_categorias_nome_por_usuario ON categorias (usuario_id, lower(nome));

CREATE TABLE tarefas (
    id              BIGSERIAL         PRIMARY KEY,
    titulo          VARCHAR(120)      NOT NULL,
    descricao       VARCHAR(2000),
    status          VARCHAR(14)       NOT NULL,
    prioridade      prioridade_tarefa NOT NULL,
    posicao         INTEGER           NOT NULL,
    categoria_id    BIGINT,
    usuario_id      BIGINT            NOT NULL,
    criada_em       TIMESTAMPTZ       NOT NULL DEFAULT now(),
    data_conclusao  TIMESTAMPTZ,

    CONSTRAINT fk_tarefas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),

    -- Chave composta: amarra a categoria ao dono da tarefa.
    -- SET NULL por coluna (PostgreSQL 15+): excluir a categoria desetiqueta as tarefas
    -- sem tocar em usuario_id, que e NOT NULL.
    CONSTRAINT fk_tarefas_categoria FOREIGN KEY (categoria_id, usuario_id)
        REFERENCES categorias (id, usuario_id)
        ON DELETE SET NULL (categoria_id),

    CONSTRAINT ck_tarefas_titulo    CHECK (titulo = btrim(titulo) AND titulo <> ''),
    CONSTRAINT ck_tarefas_descricao CHECK (descricao IS NULL OR btrim(descricao) <> ''),
    CONSTRAINT ck_tarefas_status    CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA')),
    CONSTRAINT ck_tarefas_posicao   CHECK (posicao >= 0),

    -- Os dois registros do mesmo fato nao podem divergir.
    CONSTRAINT ck_tarefas_conclusao CHECK ((status = 'CONCLUIDA') = (data_conclusao IS NOT NULL)),

    -- Ordem da faixa e, ao mesmo tempo, o indice da listagem.
    -- Deferida: a renumeracao da faixa troca posicoes dentro da transacao.
    CONSTRAINT uq_tarefas_ordem UNIQUE (usuario_id, prioridade, posicao)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE preferencias (
    id                                  BIGSERIAL   PRIMARY KEY,
    usuario_id                          BIGINT      NOT NULL,
    ocultar_concluidas_automaticamente  BOOLEAN     NOT NULL DEFAULT false,
    dias_para_ocultar                   SMALLINT    NOT NULL DEFAULT 7,

    CONSTRAINT fk_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT uq_preferencias_usuario UNIQUE (usuario_id),
    CONSTRAINT ck_preferencias_dias    CHECK (dias_para_ocultar BETWEEN 1 AND 365)
);

-- ---------------------------------------------------------------------------
-- A aplicacao nao enxerga o historico do Flyway.
--
-- O ALTER DEFAULT PRIVILEGES do criar-roles.sql concede DML em toda tabela criada pela
-- todonotes_migracao -- e o flyway_schema_history e criada por ela tambem. Sem este
-- REVOKE, a role de execucao pode APAGAR o historico de migracao, e com ele o Flyway
-- tentaria reaplicar esta migration na proxima subida: o CREATE TYPE falharia e a
-- aplicacao nao subiria mais. Conferido em 2026-11-17: antes deste REVOKE, um DELETE
-- pela todonotes_app removia a linha da V1.
--
-- Esta linha mora aqui, e nao no criar-roles.sql, por uma questao de ordem: quando aquele
-- script roda, a tabela de historico ainda nao existe. O Flyway a cria antes de rodar a
-- primeira migration, o que coloca esta migration no primeiro momento em que o REVOKE e
-- possivel.
--
-- Vale so para esta tabela. As quatro de cima continuam com DML para a aplicacao, que e
-- o que ela precisa e o limite do que ela tem.
-- ---------------------------------------------------------------------------
REVOKE ALL ON flyway_schema_history FROM todonotes_app;
