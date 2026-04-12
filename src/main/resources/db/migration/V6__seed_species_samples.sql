INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT NULL, 'PHYLUM', 'Arthropoda', '节肢动物门'
WHERE NOT EXISTS (
  SELECT 1 FROM taxon WHERE parent_id IS NULL AND `rank` = 'PHYLUM' AND scientific_name = 'Arthropoda'
);

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Reptilia', '爬行纲'
FROM taxon p
WHERE p.scientific_name = 'Chordata'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Reptilia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Actinopterygii', '辐鳍鱼纲'
FROM taxon p
WHERE p.scientific_name = 'Chordata'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Actinopterygii'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Malacostraca', '软甲纲'
FROM taxon p
WHERE p.scientific_name = 'Arthropoda'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Malacostraca'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Sirenia', '海牛目'
FROM taxon p
WHERE p.scientific_name = 'Mammalia'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Sirenia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Testudines', '龟鳖目'
FROM taxon p
WHERE p.scientific_name = 'Reptilia'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Testudines'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Perciformes', '鲈形目'
FROM taxon p
WHERE p.scientific_name = 'Actinopterygii'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Perciformes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Decapoda', '十足目'
FROM taxon p
WHERE p.scientific_name = 'Malacostraca'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Decapoda'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Dugongidae', '儒艮科'
FROM taxon p
WHERE p.scientific_name = 'Sirenia'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Dugongidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Cheloniidae', '海龟科'
FROM taxon p
WHERE p.scientific_name = 'Testudines'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Cheloniidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Sparidae', '鲷科'
FROM taxon p
WHERE p.scientific_name = 'Perciformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Sparidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Portunidae', '梭子蟹科'
FROM taxon p
WHERE p.scientific_name = 'Decapoda'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Portunidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Dugong', '儒艮属'
FROM taxon p
WHERE p.scientific_name = 'Dugongidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Dugong'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Chelonia', '海龟属'
FROM taxon p
WHERE p.scientific_name = 'Cheloniidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Chelonia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Eretmochelys', '玳瑁属'
FROM taxon p
WHERE p.scientific_name = 'Cheloniidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Eretmochelys'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Acanthopagrus', '黑鲷属'
FROM taxon p
WHERE p.scientific_name = 'Sparidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Acanthopagrus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Portunus', '梭子蟹属'
FROM taxon p
WHERE p.scientific_name = 'Portunidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Portunus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Dugong dugon', '儒艮'
FROM taxon p
WHERE p.scientific_name = 'Dugong'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Dugong dugon'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Chelonia mydas', '绿海龟'
FROM taxon p
WHERE p.scientific_name = 'Chelonia'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Chelonia mydas'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Eretmochelys imbricata', '玳瑁'
FROM taxon p
WHERE p.scientific_name = 'Eretmochelys'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Eretmochelys imbricata'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Acanthopagrus schlegelii', '黑鲷'
FROM taxon p
WHERE p.scientific_name = 'Acanthopagrus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Acanthopagrus schlegelii'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Portunus trituberculatus', '三疣梭子蟹'
FROM taxon p
WHERE p.scientific_name = 'Portunus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Portunus trituberculatus'
  );

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家一级保护野生动物',
  'VU',
  '大型海洋草食性哺乳动物，对海草床生态系统健康具有指示意义。',
  '体型粗壮，吻部向下弯曲，尾鳍呈新月形，皮肤灰褐色。',
  '常单独或小群活动，主要取食海草，活动节律受潮汐和光照影响。',
  '温暖浅海、海草床、半封闭海湾与河口近岸浅水区。',
  '主要见于南海北部、北部湾及其邻近海域。',
  20.6035000,
  109.7228000,
  '北部湾西岸与雷州半岛近海海草床',
  NULL,
  '中国海洋生物物种名录；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Dugong dugon'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家一级保护野生动物',
  'EN',
  '广布于热带和亚热带海域的大型海龟，是近海海草床与藻场的重要利用者。',
  '背甲较平滑，橄榄绿色至褐色，头部较圆，四肢呈桨状。',
  '幼体偏肉食，成体多摄食海草和藻类，具有长距离洄游习性。',
  '近岸海湾、珊瑚礁、海草床与海岛沙滩附近海域。',
  '分布于南海、东海及热带太平洋海域，中国南部海岛周边较常见。',
  20.2331000,
  110.5216000,
  '雷州半岛东部近海与南海北部海岛周边',
  NULL,
  '中国海龟保护研究资料；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Chelonia mydas'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家一级保护野生动物',
  'CR',
  '典型热带海龟物种，对珊瑚礁与海绵群落依赖度较高。',
  '喙部尖锐似鹰嘴，甲片覆瓦状排列，背甲花纹明显。',
  '多在珊瑚礁海域觅食，偏好海绵和无脊椎动物，迁移能力强。',
  '珊瑚礁、岩礁海域以及海岛周边清澈海水环境。',
  '主要分布于南海热带海区，中国南部部分岛礁海域可见。',
  20.3875000,
  110.7964000,
  '南海北部珊瑚礁海域与近海岛礁带',
  NULL,
  '中国海龟保护研究资料；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Eretmochelys imbricata'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '地方重要经济物种',
  'LC',
  '中国沿海常见近岸鱼类，是海湾和河口生态系统中的代表性种类之一。',
  '体侧扁，背部灰黑，体高较大，牙齿发达，尾鳍分叉。',
  '多栖息于中下层，幼鱼可进入河口咸淡水交汇区，杂食偏肉食。',
  '河口、海湾、近岸礁区与沙泥底海域。',
  '广泛分布于中国沿海，南海北部和雷州湾周边有稳定资源。',
  21.0489000,
  110.3338000,
  '雷州湾近岸与华南典型海湾河口带',
  NULL,
  '中国海洋鱼类图鉴；中国海洋生物物种名录',
  1
FROM taxon t
WHERE t.scientific_name = 'Acanthopagrus schlegelii'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '重要经济种',
  'NE',
  '中国近海常见大型游泳蟹类，是近海底栖生态系统和渔业资源的重要组成部分。',
  '头胸甲宽大，甲面具三枚明显疣突，最后一对步足扁平呈桨状。',
  '昼伏夜出，善于游泳和埋沙，偏肉食，摄食底栖无脊椎动物。',
  '近海沙泥底、海湾与河口外侧海域。',
  '分布于中国沿海多海区，在雷州湾与北部湾近岸海域有记录。',
  20.9732000,
  110.4621000,
  '雷州湾外海沙泥底与华南近岸海域',
  NULL,
  '中国海洋甲壳动物研究资料；中国海洋生物物种名录',
  1
FROM taxon t
WHERE t.scientific_name = 'Portunus trituberculatus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);
