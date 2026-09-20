-- Runs once, when the perf database volume is first created.
--
-- The extension lives in its own schema on purpose. Flyway runs with
-- baseline-on-migrate, and pg_stat_statements' views and functions in `public`
-- would make the schema look non-empty. Flyway would then baseline at V1 and
-- skip V1__init.sql, leaving the database without tables.
CREATE SCHEMA IF NOT EXISTS monitoring;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements SCHEMA monitoring;
