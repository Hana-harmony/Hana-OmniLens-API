DELETE FROM alert_event
WHERE source_type = 'DISCLOSURE'
  AND (
    content_availability <> 'FULL_TEXT'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"translationStatus":"TRANSLATED"%'
    OR REPLACE(event_json, ' ', '') NOT LIKE '%"translatedContent":"%'
    OR REPLACE(event_json, ' ', '') LIKE '%"translatedContent":""%'
    OR REPLACE(event_json, ' ', '') LIKE '%"translatedContent":null%'
  );
