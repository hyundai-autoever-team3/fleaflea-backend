-- Local test database only. Run after seed-local.sql (setup-perf-data.ps1 runs both).
--
-- What drives item query cost is the number of items per market, so that is
-- modelled carefully. Members and participants are only read by primary key or
-- unique index; they just need to be realistic, not large.
--
--   members       1,000 (k6-member-N@example.test, same password as k6-local)
--   participants  market 1-5: 50, 6-25: 20, 26-100: 8   (+ k6-local in every market)
--   items         market 1-5: 3,000, 6-25: 800, 26-100: 250   (~49,750 total)
--
-- Every item is sold by a participant of its market, as the create API requires.
--
-- Title keyword selectivity for search tests:
--   'rare'    ~1%   'popular' ~9%   'item'  100%

-- 1. Members. They copy k6-local's password hash, so they can log in with the
--    same password if a multi-account test is needed later.
INSERT INTO members (email, password, nickname, profile_image_key, created_at, updated_at)
SELECT 'k6-member-' || n || '@example.test', owner.password, 'k6-member-' || n, NULL,
       now() - n * interval '1 minute', now() - n * interval '1 minute'
FROM members owner
CROSS JOIN generate_series(1, 1000) AS n
WHERE owner.email = 'k6-local@example.test'
ON CONFLICT DO NOTHING;

-- 2. Participants, picked at random per market. Guarded so a re-run does not
--    keep adding participants.
INSERT INTO market_members (market_id, member_id, joined_at)
SELECT market_plan.market_id, picked.member_id, now()
FROM (
    SELECT
        market_id,
        CASE
            WHEN seq <= 5 THEN 50
            WHEN seq <= 25 THEN 20
            ELSE 8
        END AS participant_count
    FROM (
        SELECT market_id,
               CAST(substring(invite_code FROM 'k6-local-market-([0-9]+)$') AS int) AS seq
        FROM markets
        WHERE invite_code LIKE 'k6-local-market-%'
    ) AS k6_markets
) AS market_plan
CROSS JOIN LATERAL (
    SELECT candidate.member_id
    FROM members candidate
    WHERE candidate.email LIKE 'k6-member-%'
    ORDER BY random()
    LIMIT market_plan.participant_count
) AS picked
WHERE NOT EXISTS (
    SELECT 1
    FROM market_members existing
    JOIN members existing_member ON existing_member.member_id = existing.member_id
    WHERE existing_member.email LIKE 'k6-member-%'
)
ON CONFLICT (market_id, member_id) DO NOTHING;

-- 3. Items. The seller rotates through the market's participants.
--    Rows are inserted in random physical order (ORDER BY random()) so the heap
--    is not clustered by market_id. Clustered data makes an index scan look
--    better than it would be in production.
INSERT INTO items (
    market_id, collection_item_id, seller_id, title, description,
    trade_type, price, status, image_key, created_at, updated_at
)
SELECT
    market_plan.market_id,
    NULL,
    seller.member_id,
    CASE
        WHEN n % 100 = 0 THEN 'k6 rare item ' || market_plan.seq || '-' || n
        WHEN n % 10 = 0  THEN 'k6 popular item ' || market_plan.seq || '-' || n
        ELSE 'k6 item ' || market_plan.seq || '-' || n
    END,
    'Local performance test data',
    CASE n % 3 WHEN 0 THEN 'SALE' WHEN 1 THEN 'GIVEAWAY' ELSE 'RENTAL' END,
    CASE WHEN n % 3 = 0 THEN ((n % 50) + 1) * 1000 ELSE NULL END,
    CASE
        WHEN n % 10 = 0 THEN 'COMPLETED'
        WHEN n % 25 = 0 THEN 'IN_PROGRESS'
        ELSE 'AVAILABLE'
    END,
    -- Image is required (V9). Reads only build a URL string from the key, so no file is uploaded.
    'items/' || gen_random_uuid() || '.png',
    now() - (n * 100 + market_plan.seq) * interval '1 minute',
    now() - (n * 100 + market_plan.seq) * interval '1 minute'
FROM (
    SELECT
        market_id,
        seq,
        CASE
            WHEN seq <= 5 THEN 3000
            WHEN seq <= 25 THEN 800
            ELSE 250
        END AS item_count
    FROM (
        SELECT market_id,
               CAST(substring(invite_code FROM 'k6-local-market-([0-9]+)$') AS int) AS seq
        FROM markets
        WHERE invite_code LIKE 'k6-local-market-%'
    ) AS k6_markets
) AS market_plan
CROSS JOIN LATERAL generate_series(1, market_plan.item_count) AS n
JOIN (
    SELECT
        market_id,
        member_id,
        row_number() OVER (PARTITION BY market_id ORDER BY member_id) AS rn,
        count(*) OVER (PARTITION BY market_id) AS participant_count
    FROM market_members
) AS seller
    ON seller.market_id = market_plan.market_id
   AND seller.rn = (n % seller.participant_count) + 1
WHERE NOT EXISTS (SELECT 1 FROM items WHERE title = 'k6 item 1-1')
ORDER BY random();

-- Planner statistics must be fresh, otherwise before/after numbers compare
-- two different plans for reasons unrelated to the change under test.
ANALYZE members;
ANALYZE market_members;
ANALYZE items;
