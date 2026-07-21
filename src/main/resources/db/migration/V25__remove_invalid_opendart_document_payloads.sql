DELETE FROM alert_event
WHERE source_type = 'DISCLOSURE'
  AND REPLACE(event_json, ' ', '') LIKE '%"translatedContent":""%'
  AND (
      event_json LIKE '%"originalContent":"014파일이 존재하지 않습니다."%'
      OR event_json LIKE '%"originalContent":"014 파일이 존재하지 않습니다."%'
  );
