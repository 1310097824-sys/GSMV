INSERT INTO ecosystem (name, type, description)
SELECT t.name, t.type, t.description
FROM (
    SELECT '澳大利亚大堡礁外海' AS name, 'REEF' AS type, '位于澳大利亚东北部的大堡礁外围海域，兼具珊瑚礁坡面与外海观测特征。' AS description
    UNION ALL SELECT '印度尼西亚拉贾安帕特珊瑚礁', 'REEF', '印尼拉贾安帕特群岛典型高生物多样性珊瑚礁生态系统。'
    UNION ALL SELECT '菲律宾图巴塔哈珊瑚礁', 'REEF', '菲律宾苏禄海世界自然遗产级离岸珊瑚礁观测区域。'
    UNION ALL SELECT '马尔代夫北马累环礁海草床', 'SEAGRASS', '马尔代夫环礁浅海海草床与泻湖复合生态系统。'
    UNION ALL SELECT '泰国攀牙湾红树林', 'MANGROVE', '泰国攀牙湾潮沟与红树林边缘海洋生态系统。'
    UNION ALL SELECT '埃及红海珊瑚礁', 'REEF', '埃及红海近岸透明水体中的高覆盖珊瑚礁生态系统。'
    UNION ALL SELECT '肯尼亚拉穆红树林', 'MANGROVE', '东非拉穆群岛潮汐红树林与河口海湾复合生态系统。'
    UNION ALL SELECT '美国夏威夷外海', 'OFFSHORE', '夏威夷群岛附近典型热带外海与陆坡过渡海域。'
    UNION ALL SELECT '日本冲绳近海珊瑚礁', 'REEF', '冲绳庆良间群岛一带近岸珊瑚礁与礁坪生态系统。'
    UNION ALL SELECT '新西兰凯库拉外海', 'DEEP_SEA', '凯库拉陆坡海域，适合开展鲸类和外海大型生物观测。'
) t
WHERE NOT EXISTS (
    SELECT 1
    FROM ecosystem e
    WHERE e.name = t.name
);

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Pomacentridae', '雀鲷科'
FROM taxon p
WHERE p.scientific_name = 'Perciformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'FAMILY'
        AND t.scientific_name = 'Pomacentridae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Amphiprion', '双锯鱼属'
FROM taxon p
WHERE p.scientific_name = 'Pomacentridae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'GENUS'
        AND t.scientific_name = 'Amphiprion'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Amphiprion ocellaris', '小丑鱼'
FROM taxon p
WHERE p.scientific_name = 'Amphiprion'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'SPECIES'
        AND t.scientific_name = 'Amphiprion ocellaris'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Orectolobiformes', '须鲨目'
FROM taxon p
WHERE p.scientific_name = 'Chondrichthyes'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'ORDER'
        AND t.scientific_name = 'Orectolobiformes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Rhincodontidae', '鲸鲨科'
FROM taxon p
WHERE p.scientific_name = 'Orectolobiformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'FAMILY'
        AND t.scientific_name = 'Rhincodontidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Rhincodon', '鲸鲨属'
FROM taxon p
WHERE p.scientific_name = 'Rhincodontidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'GENUS'
        AND t.scientific_name = 'Rhincodon'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Rhincodon typus', '鲸鲨'
FROM taxon p
WHERE p.scientific_name = 'Rhincodon'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'SPECIES'
        AND t.scientific_name = 'Rhincodon typus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Myliobatiformes', '燕魟目'
FROM taxon p
WHERE p.scientific_name = 'Chondrichthyes'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'ORDER'
        AND t.scientific_name = 'Myliobatiformes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Mobulidae', '蝠鲼科'
FROM taxon p
WHERE p.scientific_name = 'Myliobatiformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'FAMILY'
        AND t.scientific_name = 'Mobulidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Mobula', '蝠鲼属'
FROM taxon p
WHERE p.scientific_name = 'Mobulidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'GENUS'
        AND t.scientific_name = 'Mobula'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Mobula birostris', '巨型蝠鲼'
FROM taxon p
WHERE p.scientific_name = 'Mobula'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'SPECIES'
        AND t.scientific_name = 'Mobula birostris'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Carcharhinidae', '真鲨科'
FROM taxon p
WHERE p.scientific_name = 'Carcharhiniformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'FAMILY'
        AND t.scientific_name = 'Carcharhinidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Triaenodon', '白鳍鲨属'
FROM taxon p
WHERE p.scientific_name = 'Carcharhinidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'GENUS'
        AND t.scientific_name = 'Triaenodon'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Triaenodon obesus', '白鳍礁鲨'
FROM taxon p
WHERE p.scientific_name = 'Triaenodon'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
      SELECT 1
      FROM taxon t
      WHERE t.parent_id = p.id
        AND t.`rank` = 'SPECIES'
        AND t.scientific_name = 'Triaenodon obesus'
  );

INSERT INTO species (
    taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
    distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
    t.id,
    '热带珊瑚礁指示物种',
    'LC',
    '热带珊瑚礁常见小型礁栖鱼类，常作为海葵共生与珊瑚礁健康监测的展示物种。',
    '体色橙红，体侧具白色横带，体形短而侧扁，背鳍连续。',
    '常成对或小群活动，依附海葵周边活动范围较小，主要摄食浮游生物和小型无脊椎动物。',
    '珊瑚礁、泻湖与礁坪海葵分布较高的浅海区域。',
    '分布于印度洋和西太平洋热带海域，在印尼、菲律宾、冲绳和大堡礁均较常见。',
    -0.5738000,
    130.6753000,
    '拉贾安帕特、菲律宾与澳大利亚大堡礁典型珊瑚礁海域',
    NULL,
    'Reef Fish Identification Tropical Pacific；FishBase',
    1
FROM taxon t
WHERE t.scientific_name = 'Amphiprion ocellaris'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
    taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
    distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
    t.id,
    '国家二级保护野生动物',
    'EN',
    '全球最大的鱼类之一，是热带外海和珊瑚礁外围海域的重要旗舰物种。',
    '体型巨大，背部灰蓝具浅色斑点和横纹，口宽大，尾柄粗壮。',
    '多独游或松散集群，常在高初级生产力海域和礁外海面摄食浮游生物与小型鱼类。',
    '热带和暖温带外海、岛礁外缘及季节性富营养海域。',
    '广布于热带海洋，在菲律宾、印度洋岛国和澳大利亚外海均有记录。',
    8.9492000,
    119.8245000,
    '菲律宾苏禄海、印尼东部群岛与澳大利亚东北外海',
    NULL,
    'IUCN Red List；Sharks of the World',
    1
FROM taxon t
WHERE t.scientific_name = 'Rhincodon typus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
    taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
    distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
    t.id,
    '国家二级保护野生动物',
    'EN',
    '大型洄游软骨鱼类，常出现在珊瑚礁外缘和营养较高的上升流海域。',
    '胸鳍宽大呈翼状，头宽，口前位，背面黑色，腹面白色。',
    '常单独滑翔式游动，也可在饵料丰富海面形成小群，摄食浮游动物和小型鱼群。',
    '岛礁外缘、陆坡上方和暖水外海海域。',
    '广布于热带和亚热带海洋，在马尔代夫、菲律宾、夏威夷和红海有稳定观测。',
    4.1755000,
    73.5093000,
    '马尔代夫环礁、菲律宾图巴塔哈与夏威夷外海',
    NULL,
    'IUCN Red List；Manta and Devil Rays of the World',
    1
FROM taxon t
WHERE t.scientific_name = 'Mobula birostris'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
    taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
    distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
    t.id,
    '珊瑚礁大型掠食鱼',
    'NT',
    '典型珊瑚礁掠食性鲨鱼，常见于礁坡、礁沟和礁盘边缘。',
    '体型修长，第一背鳍和尾鳍下叶白色明显，吻部钝圆。',
    '多在夜间活跃，白天可在礁穴附近巡游或停栖，摄食礁区鱼类和头足类。',
    '热带珊瑚礁、礁坡与礁缘沙地过渡带。',
    '广布于印度洋和太平洋热带珊瑚礁海域，红海、冲绳和大堡礁均较常见。',
    27.2878000,
    34.7284000,
    '红海、冲绳和大堡礁典型外礁坡面',
    NULL,
    'Sharks of the Coral Reefs；FishBase',
    1
FROM taxon t
WHERE t.scientific_name = 'Triaenodon obesus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

DROP TEMPORARY TABLE IF EXISTS temp_seed_recent_observation;
CREATE TEMPORARY TABLE temp_seed_recent_observation (
    location_name VARCHAR(128) NOT NULL,
    ecosystem_name VARCHAR(128) NOT NULL,
    observer_username VARCHAR(64) NOT NULL,
    days_ago INT NOT NULL,
    time_value TIME NOT NULL,
    location_lat DECIMAL(10, 7) NOT NULL,
    location_lng DECIMAL(10, 7) NOT NULL,
    water_temperature DECIMAL(5, 2) NOT NULL,
    salinity DECIMAL(5, 2) NOT NULL,
    ph DECIMAL(4, 2) NOT NULL,
    dissolved_oxygen DECIMAL(5, 2) NOT NULL,
    transparency DECIMAL(5, 2) NOT NULL,
    depth_meters DECIMAL(6, 2) NOT NULL,
    weather VARCHAR(32) NOT NULL,
    sea_state VARCHAR(32) NOT NULL,
    note VARCHAR(255) NOT NULL
);

INSERT INTO temp_seed_recent_observation VALUES
('湛江东里海草床样带 A1', '湛江近海', 'admin', 1, '06:45:00', 21.1829000, 110.5344000, 25.80, 31.60, 8.10, 6.40, 4.20, 6.50, '晴', '轻浪', '样例观测：湛江近海海草床晨间巡查'),
('阳江海陵岛近岸样带 B2', '阳江近海', 'admin', 2, '18:20:00', 21.7315000, 111.8651000, 24.90, 32.10, 8.05, 6.10, 3.80, 12.00, '多云', '平稳', '样例观测：阳江近岸傍晚样带调查'),
('珠江口淇澳岛潮间带 C1', '珠江口海湾', 'admin', 3, '09:05:00', 22.4142000, 113.6104000, 23.60, 24.80, 7.70, 5.80, 1.20, 2.30, '阴', '小潮', '样例观测：珠江口潮间带与河口水域联合观测'),
('珊瑚礁保育区南礁样带 D3', '珊瑚礁保育区', 'admin', 4, '15:40:00', 20.1518000, 110.6284000, 26.70, 33.40, 8.15, 6.70, 8.50, 9.80, '晴', '微浪', '样例观测：保育区南礁珊瑚覆盖复核'),
('澳大利亚大堡礁蜥蜴岛北坡 E1', '澳大利亚大堡礁外海', 'admin', 5, '07:10:00', -14.6696000, 145.4547000, 27.30, 34.80, 8.18, 6.90, 18.00, 16.00, '晴', '微浪', '样例观测：大堡礁北坡晨间潜水调查'),
('拉贾安帕特米苏尔海峡 F2', '印度尼西亚拉贾安帕特珊瑚礁', 'admin', 6, '12:55:00', -1.1327000, 130.3345000, 28.10, 34.50, 8.12, 6.80, 22.00, 14.50, '晴', '平稳', '样例观测：拉贾安帕特午间航线观测'),
('菲律宾图巴塔哈北环礁 G1', '菲律宾图巴塔哈珊瑚礁', 'admin', 7, '16:30:00', 8.9488000, 119.8201000, 28.40, 35.10, 8.20, 6.60, 26.00, 22.00, '晴', '轻浪', '样例观测：图巴塔哈外礁坡面样点巡查'),
('马尔代夫北马累环礁海草床 H3', '马尔代夫北马累环礁海草床', 'admin', 8, '08:25:00', 4.3094000, 73.5311000, 29.20, 34.60, 8.05, 6.50, 15.00, 5.60, '晴', '平稳', '样例观测：马累环礁海草床日间样方调查'),
('泰国攀牙湾红树林河口 I2', '泰国攀牙湾红树林', 'admin', 9, '17:15:00', 8.2417000, 98.5286000, 30.10, 28.90, 7.85, 5.90, 1.80, 4.20, '多云', '缓潮', '样例观测：攀牙湾红树林黄昏水道调查'),
('埃及红海赫尔格达外礁 J1', '埃及红海珊瑚礁', 'admin', 10, '10:50:00', 27.2878000, 33.7761000, 24.70, 39.40, 8.25, 7.10, 30.00, 18.00, '晴', '微浪', '样例观测：红海外礁可视样线调查'),
('肯尼亚拉穆红树林潮沟 K2', '肯尼亚拉穆红树林', 'admin', 11, '14:05:00', -2.2713000, 40.9026000, 29.40, 30.20, 7.95, 6.10, 1.60, 3.80, '阵雨', '缓潮', '样例观测：东非红树林潮沟浮游与鱼类同步调查'),
('夏威夷茂宜岛西北外海 L1', '美国夏威夷外海', 'admin', 12, '07:45:00', 20.9486000, -156.7630000, 25.10, 35.00, 8.14, 6.70, 32.00, 85.00, '晴', '中浪', '样例观测：夏威夷外海鲸类航迹观测'),
('日本冲绳庆良间群岛 M2', '日本冲绳近海珊瑚礁', 'admin', 13, '11:35:00', 26.1982000, 127.3025000, 24.30, 34.70, 8.16, 6.90, 24.00, 12.50, '晴', '轻浪', '样例观测：冲绳礁坪和泻湖样点核查'),
('新西兰凯库拉陆坡 N1', '新西兰凯库拉外海', 'admin', 14, '19:10:00', -42.4044000, 173.6838000, 17.20, 34.80, 8.11, 7.30, 18.00, 140.00, '晴', '中浪', '样例观测：凯库拉黄昏外海鲸类声学联测'),
('澳大利亚大堡礁鹦鹉岛礁坪 O4', '澳大利亚大堡礁外海', 'admin', 16, '06:20:00', -18.2867000, 147.7002000, 27.00, 34.60, 8.17, 6.80, 20.00, 11.00, '晴', '平稳', '样例观测：大堡礁礁坪清晨巡礁记录'),
('菲律宾图巴塔哈南环礁 P2', '菲律宾图巴塔哈珊瑚礁', 'admin', 18, '13:50:00', 8.8460000, 119.8038000, 28.60, 35.20, 8.18, 6.60, 25.00, 20.00, '晴', '轻浪', '样例观测：图巴塔哈南环礁午后复测'),
('拉贾安帕特瓦伊吉奥海湾 Q3', '印度尼西亚拉贾安帕特珊瑚礁', 'admin', 20, '09:30:00', -0.1969000, 130.5581000, 28.30, 34.40, 8.10, 6.70, 19.00, 8.20, '晴', '平稳', '样例观测：拉贾安帕特湾内礁盘检查'),
('珠江口伶仃洋北航道 R1', '珠江口海湾', 'admin', 23, '15:25:00', 22.3038000, 113.7096000, 22.90, 25.60, 7.68, 5.70, 1.50, 7.00, '阴', '缓流', '样例观测：伶仃洋航道附近海兽与鱼类联合记录'),
('湛江硇洲岛东南海域 S2', '湛江近海', 'admin', 26, '08:40:00', 20.9147000, 110.6012000, 25.60, 32.40, 8.09, 6.30, 6.80, 13.50, '晴', '轻浪', '样例观测：硇洲岛海草床与近岸礁区观测'),
('埃及红海沙姆沙伊赫礁坡 T3', '埃及红海珊瑚礁', 'admin', 28, '17:55:00', 27.9158000, 34.3299000, 24.90, 39.70, 8.24, 7.20, 28.00, 21.00, '晴', '微浪', '样例观测：红海礁坡黄昏大型鱼类巡查');

INSERT INTO observation (
    ecosystem_id, observer_user_id, observed_at, location_lat, location_lng,
    location_point, location_name, env_json, note
)
SELECT
    e.id,
    u.id,
    TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL s.days_ago DAY), s.time_value),
    s.location_lat,
    s.location_lng,
    ST_SRID(Point(s.location_lng, s.location_lat), 4326),
    s.location_name,
    JSON_OBJECT(
        'waterTemperature', s.water_temperature,
        'salinity', s.salinity,
        'ph', s.ph,
        'dissolvedOxygen', s.dissolved_oxygen,
        'transparency', s.transparency,
        'depthMeters', s.depth_meters,
        'weather', s.weather,
        'seaState', s.sea_state
    ),
    s.note
FROM temp_seed_recent_observation s
JOIN ecosystem e ON e.name = s.ecosystem_name
JOIN sys_user u ON u.username = s.observer_username
WHERE NOT EXISTS (
    SELECT 1
    FROM observation o
    WHERE o.location_name = s.location_name
);

DROP TEMPORARY TABLE IF EXISTS temp_seed_recent_observation_species;
CREATE TEMPORARY TABLE temp_seed_recent_observation_species (
    location_name VARCHAR(128) NOT NULL,
    scientific_name VARCHAR(128) NOT NULL,
    count_estimated INT NOT NULL,
    behavior VARCHAR(255) NULL,
    comment VARCHAR(255) NULL
);

INSERT INTO temp_seed_recent_observation_species VALUES
('湛江东里海草床样带 A1', 'Chelonia mydas', 2, '觅食', '在海草床边缘缓慢取食'),
('湛江东里海草床样带 A1', 'Hippocampus kuda', 5, '附着栖息', '附着于海草叶片与绳状附着物'),
('阳江海陵岛近岸样带 B2', 'Acanthopagrus schlegelii', 6, '巡游觅食', '在礁石和沙地交界带活动'),
('阳江海陵岛近岸样带 B2', 'Portunus trituberculatus', 4, '埋沙潜伏', '傍晚时段开始活跃'),
('阳江海陵岛近岸样带 B2', 'Sepia pharaonis', 3, '游动觅食', '靠近灯光诱集的小鱼群'),
('珠江口淇澳岛潮间带 C1', 'Sousa chinensis', 4, '通航带通过', '于航道西侧水面连续现身'),
('珠江口淇澳岛潮间带 C1', 'Crassostrea hongkongensis', 35, '附着滤食', '潮间带基底附着密度较高'),
('珊瑚礁保育区南礁样带 D3', 'Eretmochelys imbricata', 2, '巡游', '沿礁坡中层缓慢游动'),
('珊瑚礁保育区南礁样带 D3', 'Siganus fuscescens', 14, '群游摄食', '在藻类覆盖区成群活动'),
('澳大利亚大堡礁蜥蜴岛北坡 E1', 'Amphiprion ocellaris', 18, '海葵共生', '集中分布于浅礁海葵群落'),
('澳大利亚大堡礁蜥蜴岛北坡 E1', 'Triaenodon obesus', 3, '礁沟巡游', '沿礁沟边缘往返活动'),
('澳大利亚大堡礁蜥蜴岛北坡 E1', 'Mobula birostris', 2, '滑翔巡游', '在礁外海流交汇处盘旋'),
('拉贾安帕特米苏尔海峡 F2', 'Dugong dugon', 2, '取食', '在泻湖海草床持续啃食'),
('拉贾安帕特米苏尔海峡 F2', 'Chelonia mydas', 4, '巡游觅食', '与海草床伴生分布'),
('拉贾安帕特米苏尔海峡 F2', 'Siganus fuscescens', 21, '群游摄食', '在礁坡藻类区密集出现'),
('菲律宾图巴塔哈北环礁 G1', 'Rhincodon typus', 1, '表层摄食', '沿外礁坡上升流区缓慢前进'),
('菲律宾图巴塔哈北环礁 G1', 'Mobula birostris', 2, '列队游动', '两尾成对通过样线'),
('菲律宾图巴塔哈北环礁 G1', 'Sphyrna lewini', 3, '巡游', '外礁深水侧短暂经过'),
('马尔代夫北马累环礁海草床 H3', 'Dugong dugon', 1, '取食', '利用浅水海草床核心区'),
('马尔代夫北马累环礁海草床 H3', 'Chelonia mydas', 3, '觅食', '分散分布于海草床边缘'),
('马尔代夫北马累环礁海草床 H3', 'Hippocampus kuda', 7, '附着栖息', '多附着在稀疏海草和漂浮绳索'),
('泰国攀牙湾红树林河口 I2', 'Lutjanus argentimaculatus', 9, '埋伏觅食', '在红树林根系阴影区活动'),
('泰国攀牙湾红树林河口 I2', 'Hippocampus kuda', 4, '附着栖息', '潮沟边缘发现零散个体'),
('埃及红海赫尔格达外礁 J1', 'Amphiprion ocellaris', 14, '海葵共生', '浅礁区个体密度较高'),
('埃及红海赫尔格达外礁 J1', 'Triaenodon obesus', 2, '礁缘巡游', '白天静息后开始缓慢移动'),
('埃及红海赫尔格达外礁 J1', 'Eretmochelys imbricata', 1, '巡游觅食', '在礁坡海绵覆盖区活动'),
('肯尼亚拉穆红树林潮沟 K2', 'Chelonia mydas', 3, '觅食', '利用潮沟入口海草斑块'),
('肯尼亚拉穆红树林潮沟 K2', 'Lutjanus argentimaculatus', 11, '聚群', '在半咸水沟道内成群活动'),
('肯尼亚拉穆红树林潮沟 K2', 'Siganus fuscescens', 23, '摄食藻类', '分布于潮沟两侧硬底基质'),
('夏威夷茂宜岛西北外海 L1', 'Megaptera novaeangliae', 2, '跃身和喷气', '观测到母幼对伴随移动'),
('夏威夷茂宜岛西北外海 L1', 'Tursiops truncatus', 8, '伴船游动', '在船首波附近短时伴游'),
('日本冲绳庆良间群岛 M2', 'Amphiprion ocellaris', 12, '海葵共生', '礁坪浅水区分布稳定'),
('日本冲绳庆良间群岛 M2', 'Chelonia mydas', 2, '巡游', '沿沙地与礁块交界带移动'),
('日本冲绳庆良间群岛 M2', 'Siganus fuscescens', 19, '群游摄食', '在藻类较高覆盖区集中出现'),
('新西兰凯库拉陆坡 N1', 'Megaptera novaeangliae', 1, '喷气巡游', '沿陆坡北向缓慢移动'),
('新西兰凯库拉陆坡 N1', 'Tursiops truncatus', 6, '集群游动', '在鲸类外围水层伴随活动'),
('澳大利亚大堡礁鹦鹉岛礁坪 O4', 'Rhincodon typus', 2, '表层摄食', '礁坪外缘可见稳定游动轨迹'),
('澳大利亚大堡礁鹦鹉岛礁坪 O4', 'Amphiprion ocellaris', 9, '海葵共生', '礁坪泻湖海葵斑块丰富'),
('菲律宾图巴塔哈南环礁 P2', 'Mobula birostris', 4, '滑翔巡游', '潮流增强时频繁现身'),
('菲律宾图巴塔哈南环礁 P2', 'Eretmochelys imbricata', 2, '巡游觅食', '多在珊瑚高覆盖区活动'),
('菲律宾图巴塔哈南环礁 P2', 'Siganus fuscescens', 16, '群游摄食', '藻类覆盖面处数量较多'),
('拉贾安帕特瓦伊吉奥海湾 Q3', 'Triaenodon obesus', 3, '礁沟停栖', '白天多停在阴影区'),
('拉贾安帕特瓦伊吉奥海湾 Q3', 'Chelonia mydas', 2, '巡游', '湾口海草区与外礁之间往返'),
('拉贾安帕特瓦伊吉奥海湾 Q3', 'Amphiprion ocellaris', 11, '海葵共生', '湾内浅礁海葵群落分布连续'),
('珠江口伶仃洋北航道 R1', 'Sousa chinensis', 3, '通航带通过', '与中型渔船保持平行移动'),
('珠江口伶仃洋北航道 R1', 'Acanthopagrus schlegelii', 8, '近底巡游', '于航道边坡底层网目中观察到'),
('珠江口伶仃洋北航道 R1', 'Crassostrea hongkongensis', 41, '附着滤食', '潮流较缓堤体基底附着密集'),
('湛江硇洲岛东南海域 S2', 'Dugong dugon', 1, '取食', '晨间在海草斑块停留时间较长'),
('湛江硇洲岛东南海域 S2', 'Chelonia mydas', 2, '巡游觅食', '沿海草床边缘移动'),
('湛江硇洲岛东南海域 S2', 'Hippocampus kuda', 6, '附着栖息', '多附着于稀疏海草和人工渔具'),
('埃及红海沙姆沙伊赫礁坡 T3', 'Mobula birostris', 2, '滑翔巡游', '黄昏时沿礁坡上缘通过'),
('埃及红海沙姆沙伊赫礁坡 T3', 'Amphiprion ocellaris', 11, '海葵共生', '浅礁海葵区个体活跃'),
('埃及红海沙姆沙伊赫礁坡 T3', 'Triaenodon obesus', 1, '礁坡巡游', '在日落后开始离开躲藏点');

INSERT INTO observation_species (observation_id, species_id, count_estimated, behavior, comment)
SELECT
    o.id,
    s.id,
    t.count_estimated,
    t.behavior,
    t.comment
FROM temp_seed_recent_observation_species t
JOIN observation o ON o.location_name = t.location_name
JOIN species s ON s.id = (
    SELECT sp.id
    FROM species sp
    JOIN taxon tx ON tx.id = sp.taxon_id
    WHERE tx.scientific_name = t.scientific_name
      AND tx.`rank` = 'SPECIES'
    LIMIT 1
)
WHERE NOT EXISTS (
    SELECT 1
    FROM observation_species os
    WHERE os.observation_id = o.id
      AND os.species_id = s.id
);

DROP TEMPORARY TABLE IF EXISTS temp_seed_recent_observation_species;
DROP TEMPORARY TABLE IF EXISTS temp_seed_recent_observation;
