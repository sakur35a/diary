ALTER TABLE diaries
    ADD COLUMN deleted    BOOLEAN,
    ADD COLUMN deleted_at TIMESTAMPTZ;

update diaries
set deleted = false;