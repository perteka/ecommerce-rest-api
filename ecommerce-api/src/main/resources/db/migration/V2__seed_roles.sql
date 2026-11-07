INSERT INTO roles (name, created_at, updated_at)
VALUES
    ('USER', NOW(), NOW()),
    ('ADMIN', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
