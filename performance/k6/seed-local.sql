-- Local test database only. Requires the k6-local@example.test account created through /api/v1/auth/signup.
INSERT INTO collection_items (member_id, title, description, image_url, is_public, created_at, updated_at)
SELECT m.member_id, 'k6 collection item ' || n, 'Local performance test data', NULL, TRUE,
       now() - n * interval '1 minute', now() - n * interval '1 minute'
FROM members m CROSS JOIN generate_series(1, 500) AS n
WHERE m.email = 'k6-local@example.test'
  AND NOT EXISTS (SELECT 1 FROM collection_items WHERE title = 'k6 collection item 1');

INSERT INTO markets (host_id, title, description, invite_code, cover_image_url, created_at, updated_at)
SELECT m.member_id, 'k6 market ' || n, 'Local performance test data', 'k6-local-market-' || n,
       NULL, now() - n * interval '1 minute', now() - n * interval '1 minute'
FROM members m CROSS JOIN generate_series(1, 100) AS n
WHERE m.email = 'k6-local@example.test'
ON CONFLICT (invite_code) DO NOTHING;

INSERT INTO market_members (market_id, member_id, joined_at)
SELECT market.market_id, m.member_id, now()
FROM markets market CROSS JOIN members m
WHERE market.invite_code LIKE 'k6-local-market-%'
  AND m.email = 'k6-local@example.test'
ON CONFLICT (market_id, member_id) DO NOTHING;

INSERT INTO friendships (requester_id, addressee_id, status, created_at, updated_at)
SELECT requester.member_id, owner.member_id, 'ACCEPTED', now(), now()
FROM members requester CROSS JOIN members owner
WHERE requester.email = 'k6-requester@example.test'
  AND owner.email = 'k6-local@example.test'
  AND NOT EXISTS (
      SELECT 1 FROM friendships friendship
      WHERE friendship.requester_id = requester.member_id
        AND friendship.addressee_id = owner.member_id
  );

INSERT INTO collection_trade_requests
    (collection_item_id, requester_id, trade_type, status, requester_confirmed, owner_confirmed, created_at, updated_at)
SELECT item.collection_item_id, requester.member_id, 'RENTAL', 'PENDING', FALSE, FALSE, now(), now()
FROM collection_items item CROSS JOIN members requester
WHERE item.title = 'k6 collection item 1'
  AND requester.email = 'k6-requester@example.test'
  AND NOT EXISTS (
      SELECT 1 FROM collection_trade_requests request
      WHERE request.collection_item_id = item.collection_item_id
        AND request.requester_id = requester.member_id
  );
