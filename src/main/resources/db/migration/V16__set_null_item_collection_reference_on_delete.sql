ALTER TABLE items
    DROP CONSTRAINT fk_items_collection_item;

ALTER TABLE items
    ADD CONSTRAINT fk_items_collection_item
        FOREIGN KEY (collection_item_id)
            REFERENCES collection_items (collection_item_id)
            ON DELETE SET NULL;
