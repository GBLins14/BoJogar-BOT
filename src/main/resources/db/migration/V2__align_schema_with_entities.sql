-- Run this SQL manually on the database to align schema with current JPA entities.
-- Flyway is NOT configured in this project, so this won't run automatically.

-- 1. Drop old FK constraint on organizador_id (was referencing organizadores table, now needs to reference users)
ALTER TABLE peladas DROP CONSTRAINT IF EXISTS peladas_organizador_id_fkey;

-- 2. Populate organizador_id from created_by_id for any existing rows (if created_by_id was used before)
UPDATE peladas SET organizador_id = created_by_id WHERE organizador_id IS NULL AND created_by_id IS NOT NULL;

-- 3. Drop the now-unused created_by_id column
ALTER TABLE peladas DROP COLUMN IF EXISTS created_by_id;

-- 4. Add new FK constraint pointing to users table
ALTER TABLE peladas ADD CONSTRAINT peladas_organizador_id_fkey FOREIGN KEY (organizador_id) REFERENCES users(id);
