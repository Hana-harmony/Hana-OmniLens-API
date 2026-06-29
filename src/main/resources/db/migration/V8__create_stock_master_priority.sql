CREATE TABLE IF NOT EXISTS stock_master_priority (
    stock_code VARCHAR(6) PRIMARY KEY,
    priority_rank INTEGER NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO stock_master_priority (stock_code, priority_rank)
VALUES
    ('005930', 1),
    ('000660', 2),
    ('373220', 3),
    ('207940', 4),
    ('005380', 5),
    ('000270', 6),
    ('005490', 7),
    ('051910', 8),
    ('035420', 9),
    ('035720', 10),
    ('068270', 11),
    ('105560', 12),
    ('055550', 13),
    ('086790', 14),
    ('316140', 15),
    ('012330', 16),
    ('066570', 17),
    ('096770', 18),
    ('034730', 19),
    ('003550', 20),
    ('017670', 21),
    ('033780', 22),
    ('015760', 23),
    ('009150', 24),
    ('010130', 25),
    ('028260', 26),
    ('032830', 27),
    ('006400', 28),
    ('018260', 29),
    ('247540', 30);
