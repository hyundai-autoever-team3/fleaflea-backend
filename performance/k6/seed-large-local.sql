-- Local test database only. Run after seed-local.sql to model a larger collection.
INSERT INTO collection_items (member_id, title, description, image_url, is_public, created_at, updated_at)
SELECT m.member_id, 'k6 collection item ' || n, 'Local performance test data', NULL, TRUE,
       now() - n * interval '1 minute', now() - n * interval '1 minute'
FROM members m CROSS JOIN generate_series(501, 50000) AS n
WHERE m.email = 'k6-local@example.test'
  AND NOT EXISTS (SELECT 1 FROM collection_items WHERE title = 'k6 collection item 50000');
