ALTER TABLE beg_requests
    ADD COLUMN collection_item_snapshot_id BIGINT,
    ADD COLUMN collection_item_title VARCHAR(150),
    ADD COLUMN collection_item_description TEXT;

UPDATE beg_requests request
SET collection_item_snapshot_id = item.collection_item_id,
    collection_item_title = item.title,
    collection_item_description = item.description
FROM collection_items item
WHERE item.collection_item_id = request.collection_item_id;

UPDATE beg_requests
SET collection_item_snapshot_id = collection_item_id,
    collection_item_title = '[삭제된 물건]'
WHERE collection_item_snapshot_id IS NULL;

ALTER TABLE beg_requests
    ALTER COLUMN collection_item_snapshot_id SET NOT NULL,
    ALTER COLUMN collection_item_title SET NOT NULL,
    ALTER COLUMN collection_item_id DROP NOT NULL;

ALTER TABLE collection_trade_requests
    ADD COLUMN collection_item_snapshot_id BIGINT,
    ADD COLUMN collection_item_title VARCHAR(150),
    ADD COLUMN collection_item_description TEXT,
    ADD COLUMN offer_collection_item_snapshot_id BIGINT,
    ADD COLUMN offer_collection_item_title VARCHAR(150),
    ADD COLUMN offer_collection_item_description TEXT;

UPDATE collection_trade_requests request
SET collection_item_snapshot_id = item.collection_item_id,
    collection_item_title = item.title,
    collection_item_description = item.description
FROM collection_items item
WHERE item.collection_item_id = request.collection_item_id;

UPDATE collection_trade_requests
SET collection_item_snapshot_id = collection_item_id,
    collection_item_title = '[삭제된 물건]'
WHERE collection_item_snapshot_id IS NULL;

UPDATE collection_trade_requests request
SET offer_collection_item_snapshot_id = item.collection_item_id,
    offer_collection_item_title = item.title,
    offer_collection_item_description = item.description
FROM collection_items item
WHERE item.collection_item_id = request.offer_collection_item_id;

ALTER TABLE collection_trade_requests
    ALTER COLUMN collection_item_snapshot_id SET NOT NULL,
    ALTER COLUMN collection_item_title SET NOT NULL,
    ALTER COLUMN collection_item_id DROP NOT NULL;

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid = 'collection_items'::regclass
          AND conrelid IN (
              'items'::regclass,
              'beg_requests'::regclass,
              'collection_trade_requests'::regclass
          )
    LOOP
        EXECUTE format(
                'ALTER TABLE %s DROP CONSTRAINT %I',
                constraint_record.table_name,
                constraint_record.conname
        );
    END LOOP;
END $$;

-- 기존 운영 데이터에는 과거 삭제 로직으로 인해 실제 도감 아이템이 없는
-- 참조 ID가 남아 있을 수 있다. 원래 ID는 위 스냅샷 컬럼에 보존하고,
-- 외래키 컬럼만 NULL로 정리한 뒤 ON DELETE SET NULL 제약을 다시 만든다.
UPDATE items item
SET collection_item_id = NULL
WHERE collection_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM collection_items collection_item
      WHERE collection_item.collection_item_id = item.collection_item_id
  );

UPDATE beg_requests request
SET collection_item_id = NULL
WHERE collection_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM collection_items collection_item
      WHERE collection_item.collection_item_id = request.collection_item_id
  );

UPDATE collection_trade_requests request
SET collection_item_id = NULL
WHERE collection_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM collection_items collection_item
      WHERE collection_item.collection_item_id = request.collection_item_id
  );

UPDATE collection_trade_requests request
SET offer_collection_item_id = NULL
WHERE offer_collection_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM collection_items collection_item
      WHERE collection_item.collection_item_id = request.offer_collection_item_id
  );

ALTER TABLE items
    ADD CONSTRAINT fk_items_collection_item
        FOREIGN KEY (collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL;

ALTER TABLE beg_requests
    ADD CONSTRAINT fk_beg_requests_collection_item
        FOREIGN KEY (collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL;

ALTER TABLE collection_trade_requests
    ADD CONSTRAINT fk_collection_trade_requests_collection_item
        FOREIGN KEY (collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL,
    ADD CONSTRAINT fk_collection_trade_requests_offer_collection_item
        FOREIGN KEY (offer_collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL;
