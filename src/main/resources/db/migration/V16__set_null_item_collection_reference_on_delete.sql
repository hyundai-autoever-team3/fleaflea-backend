-- 초기 운영 DB는 Hibernate 생성 스키마를 V1로 baseline하여 FK 이름이 환경마다 다를 수 있다.
DO $$
DECLARE
    existing_constraint RECORD;
BEGIN
    FOR existing_constraint IN
        SELECT DISTINCT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_attribute column_info
          ON column_info.attrelid = constraint_info.conrelid
         AND column_info.attnum = ANY (constraint_info.conkey)
        WHERE constraint_info.conrelid = 'items'::regclass
          AND constraint_info.contype = 'f'
          AND column_info.attname = 'collection_item_id'
    LOOP
        EXECUTE format(
                'ALTER TABLE items DROP CONSTRAINT %I',
                existing_constraint.conname
        );
    END LOOP;
END
$$;

ALTER TABLE items
    ADD CONSTRAINT fk_items_collection_item
        FOREIGN KEY (collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL;
