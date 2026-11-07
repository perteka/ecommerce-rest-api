CREATE UNIQUE INDEX uk_categories_name_lower ON categories (lower(name));

CREATE INDEX idx_order_items_product_id ON order_items (product_id);

CREATE INDEX idx_products_active_category ON products (active, category_id);
CREATE INDEX idx_products_active_price ON products (active, price);
