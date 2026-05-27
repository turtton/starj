CREATE TABLE storage_objects (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    object_key VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_storage_objects_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE INDEX idx_storage_objects_owner_id ON storage_objects(owner_id, created_at DESC);
