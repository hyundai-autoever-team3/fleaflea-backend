-- Execution plans for the item read queries, run against the perf database after
-- setup-perf-data.ps1. Run it once before and once after each optimization and
-- compare the saved outputs.
--
--   Get-Content .\performance\k6\explain-items.sql -Raw `
--     | docker exec -i fleaflea-perf-db psql -U postgres -d fleaflea_db `
--     | Out-File -Encoding utf8 .\performance\k6\results\explain-<label>.txt
--
-- Run the file twice and keep the second output: the first run reads pages from
-- disk, the second from cache, and the app sees the cached case.
--
-- The statements are copied from what the app actually sends (taken from
-- monitoring.pg_stat_statements) for ItemRepositoryImpl.searchInMarket (QueryDSL).
-- Only conditions with a value are part of the SQL, so every filter combination
-- is its own statement. If the repository query changes, copy the new SQL from
-- monitoring.pg_stat_statements. The version used for the baseline and the V10
-- measurement ($2 IS NULL OR ... for every optional filter) is in git history.
--
-- The app runs these as JDBC prepared statements with bound parameters, so they
-- are PREPAREd here too instead of EXPLAINing SQL with literals.

\pset pager off
\set ON_ERROR_STOP on

-- Measurement targets, resolved by the seed's invite codes so IDs need not be known.
SELECT market_id AS large_market FROM markets WHERE invite_code = 'k6-local-market-1' \gset
SELECT market_id AS small_market FROM markets WHERE invite_code = 'k6-local-market-100' \gset
SELECT member_id AS viewer FROM members WHERE email = 'k6-local@example.test' \gset
SELECT item_id AS sample_item FROM items WHERE market_id = :large_market ORDER BY item_id LIMIT 1 \gset

\echo
\echo '=================================================================='
\echo ' Current state'
\echo '=================================================================='
SELECT
    (SELECT count(*) FROM items)                                AS items_total,
    (SELECT count(*) FROM items WHERE market_id = :large_market) AS items_large_market,
    (SELECT count(*) FROM items WHERE market_id = :small_market) AS items_small_market,
    pg_size_pretty(pg_table_size('items'))                      AS items_table_size,
    pg_size_pretty(pg_indexes_size('items'))                    AS items_index_size;

SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'items' ORDER BY indexname;

-- The keyword pattern ('%' || keyword || '%') is built in Java, so it is passed
-- here already wrapped. The app also sends an ESCAPE parameter, which does not
-- change the plan and is left out.

-- List: no filter.  $1 market, $2 offset, $3 page size
PREPARE list_all(bigint, bigint, int) AS
SELECT i.item_id, i.collection_item_id, i.created_at, i.description, i.image_key,
       i.market_id, i.price, i.seller_id, i.status, i.title, i.trade_type, i.updated_at
FROM items i
WHERE i.market_id = $1
ORDER BY i.created_at DESC, i.item_id DESC
OFFSET $2 ROWS FETCH FIRST $3 ROWS ONLY;

-- List: tradeType + status.  $1 market, $2 tradeType, $3 status, $4 offset, $5 page size
PREPARE list_filtered(bigint, varchar, varchar, bigint, int) AS
SELECT i.item_id, i.collection_item_id, i.created_at, i.description, i.image_key,
       i.market_id, i.price, i.seller_id, i.status, i.title, i.trade_type, i.updated_at
FROM items i
WHERE i.market_id = $1 AND i.trade_type = $2 AND i.status = $3
ORDER BY i.created_at DESC, i.item_id DESC
OFFSET $4 ROWS FETCH FIRST $5 ROWS ONLY;

-- List: keyword.  $1 market, $2 pattern, $3 offset, $4 page size
PREPARE list_keyword(bigint, varchar, bigint, int) AS
SELECT i.item_id, i.collection_item_id, i.created_at, i.description, i.image_key,
       i.market_id, i.price, i.seller_id, i.status, i.title, i.trade_type, i.updated_at
FROM items i
WHERE i.market_id = $1 AND lower(i.title) LIKE lower($2)
ORDER BY i.created_at DESC, i.item_id DESC
OFFSET $3 ROWS FETCH FIRST $4 ROWS ONLY;

-- Count queries. PageableExecutionUtils skips them when the first page is not full
-- or the requested page is the last one.
PREPARE count_all(bigint) AS
SELECT count(i.item_id) FROM items i WHERE i.market_id = $1;

PREPARE count_filtered(bigint, varchar, varchar) AS
SELECT count(i.item_id) FROM items i WHERE i.market_id = $1 AND i.trade_type = $2 AND i.status = $3;

PREPARE count_keyword(bigint, varchar) AS
SELECT count(i.item_id) FROM items i WHERE i.market_id = $1 AND lower(i.title) LIKE lower($2);

-- ItemService.validateMarketExists (existsById)
PREPARE market_exists(bigint) AS
SELECT count(*) FROM markets m WHERE m.market_id = $1;

-- ItemService.validateMarketParticipant
PREPARE participant_exists(bigint, bigint) AS
SELECT mm.market_member_id FROM market_members mm
WHERE mm.market_id = $1 AND mm.member_id = $2
FETCH FIRST 1 ROWS ONLY;

-- ItemRepository.findWithSellerByItemId
PREPARE item_detail(bigint) AS
SELECT i.*, s.member_id, s.email, s.nickname, s.profile_image_key
FROM items i
JOIN members s ON s.member_id = i.seller_id
WHERE i.item_id = $1;

-- Custom plans (the first executions of a prepared statement) are pinned so
-- that every case below is planned the same way regardless of execution order.
SET plan_cache_mode = force_custom_plan;

\echo
\echo '=================================================================='
\echo ' [1] List: large market (3,000 items), no filter, page 0'
\echo '     The most common request.'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_all(:large_market, 0, 20);

\echo
\echo '=================================================================='
\echo ' [2] List: small market (250 items), no filter, page 0'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_all(:small_market, 0, 20);

\echo
\echo '=================================================================='
\echo ' [3] List: large market, tradeType=SALE, status=AVAILABLE'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_filtered(:large_market, 'SALE', 'AVAILABLE', 0, 20);

\echo
\echo '=================================================================='
\echo ' [4] List: large market, keyword "rare" (~1% match)'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_keyword(:large_market, '%rare%', 0, 20);

\echo
\echo '=================================================================='
\echo ' [5] List: large market, keyword "item" (100% match)'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_keyword(:large_market, '%item%', 0, 20);

\echo
\echo '=================================================================='
\echo ' [6] List: large market, page 50 (OFFSET 1000)'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_all(:large_market, 1000, 20);

\echo
\echo '=================================================================='
\echo ' [7] Count: large market, no filter / SALE+AVAILABLE / keyword "rare"'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE count_all(:large_market);
EXPLAIN (ANALYZE, BUFFERS) EXECUTE count_filtered(:large_market, 'SALE', 'AVAILABLE');
EXPLAIN (ANALYZE, BUFFERS) EXECUTE count_keyword(:large_market, '%rare%');

\echo
\echo '=================================================================='
\echo ' [8] Validation queries run on every list / detail request'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE market_exists(:large_market);
EXPLAIN (ANALYZE, BUFFERS) EXECUTE participant_exists(:large_market, :viewer);

\echo
\echo '=================================================================='
\echo ' [9] Detail: item joined with seller'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE item_detail(:sample_item);

-- After a few executions the JDBC driver switches to a server-side prepared
-- statement, and PostgreSQL may reuse one generic plan for every parameter set.
SET plan_cache_mode = force_generic_plan;

\echo
\echo '=================================================================='
\echo ' [10] Generic plan: list [1] and count [7] as a long-running app may run them'
\echo '=================================================================='
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list_all(:large_market, 0, 20);
EXPLAIN (ANALYZE, BUFFERS) EXECUTE count_all(:large_market);

RESET plan_cache_mode;
DEALLOCATE ALL;
