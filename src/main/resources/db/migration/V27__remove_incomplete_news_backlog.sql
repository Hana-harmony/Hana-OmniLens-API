DELETE FROM alert_event
WHERE source_type IN ('NEWS', 'DISCLOSURE')
  AND (
    content_availability <> 'FULL_TEXT'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"translationStatus":"TRANSLATED"%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"originalContent":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"originalContent":""%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"translatedTitle":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"translatedTitle":""%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"translatedContent":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"translatedContent":""%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"what":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"what":""%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"why":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"why":""%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"impact":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"impact":""%'
  );

DELETE FROM market_news_event
WHERE REPLACE(event_json, ' ', '') NOT LIKE '%"contentAvailability":"FULL_TEXT"%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"translationStatus":"TRANSLATED"%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"originalContent":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"originalContent":""%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"translatedTitle":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"translatedTitle":""%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"translatedContent":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"translatedContent":""%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"what":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"what":""%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"why":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"why":""%'
   OR REPLACE(event_json, ' ', '') NOT LIKE '%"impact":"%'
   OR REPLACE(event_json, ' ', '') LIKE '%"impact":""%';
