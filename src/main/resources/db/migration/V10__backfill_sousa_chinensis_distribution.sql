UPDATE species s
JOIN taxon t ON t.id = s.taxon_id
SET
    s.distribution_lat = COALESCE(s.distribution_lat, 22.3865000),
    s.distribution_lng = COALESCE(s.distribution_lng, 113.8246000),
    s.geo_range_text = CASE
        WHEN s.geo_range_text IS NULL OR s.geo_range_text = '' THEN '珠江口伶仃洋、淇澳岛至担杆列岛近岸海域'
        ELSE s.geo_range_text
    END,
    s.updated_at = CURRENT_TIMESTAMP(3)
WHERE t.scientific_name = 'Sousa chinensis'
  AND t.`rank` = 'SPECIES';
