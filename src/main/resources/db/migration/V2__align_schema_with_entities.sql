-- Run this SQL manually on the database to align schema with current JPA entities.
-- Flyway is NOT configured in this project, so this won't run automatically.

-- ============================================================
-- 1. PELADAS: organizador_id FK fix
-- ============================================================

-- Drop old FK (was referencing organizadores table, now needs to reference users)
ALTER TABLE peladas DROP CONSTRAINT IF EXISTS peladas_organizador_id_fkey;

-- Populate organizador_id from created_by_id for any existing rows
UPDATE peladas SET organizador_id = created_by_id WHERE organizador_id IS NULL AND created_by_id IS NOT NULL;

-- Drop the now-unused created_by_id column
ALTER TABLE peladas DROP COLUMN IF EXISTS created_by_id;

-- Add new FK pointing to users table
ALTER TABLE peladas ADD CONSTRAINT peladas_organizador_id_fkey FOREIGN KEY (organizador_id) REFERENCES users(id);

-- ============================================================
-- 2. PAGAMENTOS: inscricao_id FK fix
-- ============================================================

-- Drop old FK (was referencing inscricoes table, now needs to reference pelada_participants)
ALTER TABLE pagamentos DROP CONSTRAINT IF EXISTS pagamentos_inscricao_id_fkey;

-- Drop the now-unused participant_id column (created by Hibernate ddl-auto)
ALTER TABLE pagamentos DROP COLUMN IF EXISTS participant_id;

-- Add new FK pointing to pelada_participants table
ALTER TABLE pagamentos ADD CONSTRAINT pagamentos_inscricao_id_fkey FOREIGN KEY (inscricao_id) REFERENCES pelada_participants(id);

-- ============================================================
-- 3. Cleanup: old V1 tables that are no longer used
-- ============================================================

-- inscricoes was replaced by pelada_participants
-- jogadores was replaced by users
-- organizadores was replaced by users
-- Only drop these AFTER verifying no data needs migrating!

-- DROP TABLE IF EXISTS inscricoes CASCADE;
-- DROP TABLE IF EXISTS jogadores CASCADE;
-- DROP TABLE IF EXISTS organizadores CASCADE;
