UPDATE stock_master_priority
SET priority_rank = priority_rank + 100,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM stock_master_priority
WHERE stock_code IN (
    '005930', '000660', '005380', '000270', '086790',
    '035420', '068270', '105560', '055550', '012330'
);

INSERT INTO stock_master_priority (stock_code, priority_rank)
VALUES
    ('005930', 1),
    ('000660', 2),
    ('005380', 3),
    ('000270', 4),
    ('086790', 5),
    ('035420', 6),
    ('068270', 7),
    ('105560', 8),
    ('055550', 9),
    ('012330', 10);
