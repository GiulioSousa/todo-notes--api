-- Preparacao de uma vez so do banco todonotes_db: separa quem monta o schema de quem
-- opera sobre ele.
--
-- Rode como superusuario ANTES da primeira subida da aplicacao. Este arquivo e versionado
-- e NAO pode conter segredo, entao trabalhe sobre uma copia, que o .gitignore ignora:
--
--   cp db/criar-roles.sql db/criar-roles.local.sql
--   (troque os dois placeholders de senha na copia)
--   psql -h localhost -p 5433 -U postgres -d todonotes_db -f db/criar-roles.local.sql
--
-- O banco precisa existir antes:
--   psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE todonotes_db"
--
-- As senhas reais vao para application-local.yml, tambem ignorado pelo Git.
--
-- Nao e uma migration do Flyway de proposito: criar role exige superusuario, e o Flyway
-- roda justamente *como* a role de migracao, que neste ponto ainda nao existe.
--
-- O eixo de todo o arquivo: o Flyway conecta como todonotes_migracao, e a aplicacao como
-- todonotes_app. Se as duas conexoes do application.yml nao estiverem assim, os
-- privilegios padrao do fim do script nao alcancam nada.

-- Interrompe no primeiro erro, em vez de seguir e deixar o banco meio configurado.
\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------------
-- As duas roles. Guardadas por IF NOT EXISTS para o script poder ser reexecutado.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'todonotes_migracao') THEN
        CREATE ROLE todonotes_migracao LOGIN PASSWORD 'SENHA_DA_MIGRACAO';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'todonotes_app') THEN
        CREATE ROLE todonotes_app LOGIN PASSWORD 'SENHA_DA_APLICACAO';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Acesso ao schema.
--
-- O CREATE nao e defensivo: desde o PostgreSQL 15 o schema public deixou de ser
-- gravavel por qualquer role, e sem este grant a V1 falha na primeira subida.
-- ---------------------------------------------------------------------------
GRANT USAGE, CREATE ON SCHEMA public TO todonotes_migracao;
GRANT USAGE               ON SCHEMA public TO todonotes_app;

-- ---------------------------------------------------------------------------
-- Privilegios padrao para o que as migrations criarem daqui em diante.
--
-- Num banco novo, TODAS as tabelas sao "criadas daqui em diante", e por isso este bloco
-- e o que da acesso a todonotes_app. Ele precisa rodar antes da primeira subida: sem
-- ele, a aplicacao sobe e nao enxerga tabela alguma.
-- ---------------------------------------------------------------------------
ALTER DEFAULT PRIVILEGES FOR ROLE todonotes_migracao IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO todonotes_app;

-- O INSERT depende das sequences dos BIGSERIAL.
ALTER DEFAULT PRIVILEGES FOR ROLE todonotes_migracao IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO todonotes_app;

-- O tipo prioridade_tarefa, criado pela V1, nao precisa de grant: o PostgreSQL concede
-- USAGE em tipos a PUBLIC por padrao. Fica dito para nao parecer esquecimento.

-- Nada para flyway_schema_history: a aplicacao nao tem o que fazer com o historico das
-- migrations, e a tabela nasce pertencendo a todonotes_migracao.

-- ---------------------------------------------------------------------------
-- Quem pode sequer conectar neste banco.
--
-- Por padrao o PostgreSQL concede CONNECT a PUBLIC, isto e, a qualquer role da
-- instancia — e esta instancia e compartilhada com o GeFinX. Restringir fecha o
-- todonotes_db para quem nao e deste projeto.
--
-- O caminho inverso, impedir que todonotes_app conecte em OUTROS bancos, nao se resolve
-- aqui: depende de revogar CONNECT de PUBLIC naqueles bancos, decisao da instancia
-- inteira e nao deste projeto.
-- ---------------------------------------------------------------------------
REVOKE CONNECT ON DATABASE todonotes_db FROM PUBLIC;
GRANT  CONNECT ON DATABASE todonotes_db TO todonotes_migracao, todonotes_app;

-- ---------------------------------------------------------------------------
-- Conferencia. Esperado: duas linhas, as duas com rolcanlogin = t e rolsuper = f.
-- ---------------------------------------------------------------------------
SELECT rolname, rolcanlogin, rolsuper, rolcreatedb, rolcreaterole
  FROM pg_roles
 WHERE rolname LIKE 'todonotes%'
 ORDER BY rolname;
