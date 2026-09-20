-- Exports the k6 test dataset as a single JSON line for item-scenarios.js.
-- Hot markets (seq 1-5) carry most of the traffic; the rest are the long tail.
-- Run through setup-perf-data.ps1, which saves the output without a BOM.

WITH k6_markets AS (
    SELECT
        market_id,
        CAST(substring(invite_code FROM 'k6-local-market-([0-9]+)$') AS int) AS seq
    FROM markets
    WHERE invite_code LIKE 'k6-local-market-%'
)
SELECT json_build_object(
    'hotMarkets', (
        SELECT coalesce(json_agg(market_id), '[]'::json)
        FROM k6_markets WHERE seq <= 5
    ),
    'coldMarkets', (
        SELECT coalesce(json_agg(market_id), '[]'::json)
        FROM k6_markets WHERE seq > 5
    ),
    'hotItems', (
        SELECT coalesce(json_agg(item_id), '[]'::json)
        FROM (
            SELECT i.item_id
            FROM items i
            JOIN k6_markets k ON k.market_id = i.market_id
            WHERE k.seq <= 5
            ORDER BY random()
            LIMIT 1000
        ) sampled
    ),
    'coldItems', (
        SELECT coalesce(json_agg(item_id), '[]'::json)
        FROM (
            SELECT i.item_id
            FROM items i
            JOIN k6_markets k ON k.market_id = i.market_id
            WHERE k.seq > 5
            ORDER BY random()
            LIMIT 1000
        ) sampled
    )
)::text;
