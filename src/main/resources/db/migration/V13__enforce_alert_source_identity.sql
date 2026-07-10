DELETE FROM alert_event
WHERE alert_id NOT IN (
    SELECT MAX(alert_id)
    FROM alert_event
    GROUP BY partner_id, stock_code, source_type, original_url
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_alert_event_source_identity
    ON alert_event (partner_id, stock_code, source_type, original_url);
