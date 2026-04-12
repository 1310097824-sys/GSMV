DELETE os
FROM observation_species os
JOIN species s ON s.id = os.species_id
JOIN taxon t ON t.id = s.taxon_id
WHERE t.`rank` <> 'SPECIES';

DELETE s
FROM species s
JOIN taxon t ON t.id = s.taxon_id
WHERE t.`rank` <> 'SPECIES';

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Balaenopteridae', '须鲸科'
FROM taxon p
WHERE p.scientific_name = 'Cetacea'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Balaenopteridae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Tursiops', '瓶鼻海豚属'
FROM taxon p
WHERE p.scientific_name = 'Delphinidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Tursiops'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Megaptera', '座头鲸属'
FROM taxon p
WHERE p.scientific_name = 'Balaenopteridae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Megaptera'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Tursiops truncatus', '宽吻海豚'
FROM taxon p
WHERE p.scientific_name = 'Tursiops'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Tursiops truncatus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Megaptera novaeangliae', '座头鲸'
FROM taxon p
WHERE p.scientific_name = 'Megaptera'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Megaptera novaeangliae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Chondrichthyes', '软骨鱼纲'
FROM taxon p
WHERE p.scientific_name = 'Chordata'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Chondrichthyes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Carcharhiniformes', '真鲨目'
FROM taxon p
WHERE p.scientific_name = 'Chondrichthyes'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Carcharhiniformes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Sphyrnidae', '双髻鲨科'
FROM taxon p
WHERE p.scientific_name = 'Carcharhiniformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Sphyrnidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Sphyrna', '双髻鲨属'
FROM taxon p
WHERE p.scientific_name = 'Sphyrnidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Sphyrna'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Sphyrna lewini', '路氏双髻鲨'
FROM taxon p
WHERE p.scientific_name = 'Sphyrna'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Sphyrna lewini'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Syngnathiformes', '海龙目'
FROM taxon p
WHERE p.scientific_name = 'Actinopterygii'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Syngnathiformes'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Syngnathidae', '海龙科'
FROM taxon p
WHERE p.scientific_name = 'Syngnathiformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Syngnathidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Hippocampus', '海马属'
FROM taxon p
WHERE p.scientific_name = 'Syngnathidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Hippocampus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Hippocampus kuda', '海马'
FROM taxon p
WHERE p.scientific_name = 'Hippocampus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Hippocampus kuda'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Moronidae', '狼鲈科'
FROM taxon p
WHERE p.scientific_name = 'Perciformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Moronidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Lutjanidae', '笛鲷科'
FROM taxon p
WHERE p.scientific_name = 'Perciformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Lutjanidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Siganidae', '篮子鱼科'
FROM taxon p
WHERE p.scientific_name = 'Perciformes'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Siganidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Lateolabrax', '花鲈属'
FROM taxon p
WHERE p.scientific_name = 'Moronidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Lateolabrax'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Lutjanus', '笛鲷属'
FROM taxon p
WHERE p.scientific_name = 'Lutjanidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Lutjanus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Siganus', '篮子鱼属'
FROM taxon p
WHERE p.scientific_name = 'Siganidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Siganus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Lateolabrax maculatus', '花鲈'
FROM taxon p
WHERE p.scientific_name = 'Lateolabrax'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Lateolabrax maculatus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Lutjanus argentimaculatus', '红鳍笛鲷'
FROM taxon p
WHERE p.scientific_name = 'Lutjanus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Lutjanus argentimaculatus'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Siganus fuscescens', '褐篮子鱼'
FROM taxon p
WHERE p.scientific_name = 'Siganus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Siganus fuscescens'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT NULL, 'PHYLUM', 'Mollusca', '软体动物门'
WHERE NOT EXISTS (
  SELECT 1 FROM taxon WHERE parent_id IS NULL AND `rank` = 'PHYLUM' AND scientific_name = 'Mollusca'
);

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Cephalopoda', '头足纲'
FROM taxon p
WHERE p.scientific_name = 'Mollusca'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Cephalopoda'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Bivalvia', '双壳纲'
FROM taxon p
WHERE p.scientific_name = 'Mollusca'
  AND p.`rank` = 'PHYLUM'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'CLASS' AND t.scientific_name = 'Bivalvia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Sepiida', '乌贼目'
FROM taxon p
WHERE p.scientific_name = 'Cephalopoda'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Sepiida'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Ostreoida', '牡蛎目'
FROM taxon p
WHERE p.scientific_name = 'Bivalvia'
  AND p.`rank` = 'CLASS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'ORDER' AND t.scientific_name = 'Ostreoida'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Sepiidae', '乌贼科'
FROM taxon p
WHERE p.scientific_name = 'Sepiida'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Sepiidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Ostreidae', '牡蛎科'
FROM taxon p
WHERE p.scientific_name = 'Ostreoida'
  AND p.`rank` = 'ORDER'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'FAMILY' AND t.scientific_name = 'Ostreidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Sepia', '乌贼属'
FROM taxon p
WHERE p.scientific_name = 'Sepiidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Sepia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Crassostrea', '牡蛎属'
FROM taxon p
WHERE p.scientific_name = 'Ostreidae'
  AND p.`rank` = 'FAMILY'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'GENUS' AND t.scientific_name = 'Crassostrea'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Sepia pharaonis', '花斑乌贼'
FROM taxon p
WHERE p.scientific_name = 'Sepia'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Sepia pharaonis'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Crassostrea hongkongensis', '香港牡蛎'
FROM taxon p
WHERE p.scientific_name = 'Crassostrea'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Crassostrea hongkongensis'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Portunus sanguinolentus', '红星梭子蟹'
FROM taxon p
WHERE p.scientific_name = 'Portunus'
  AND p.`rank` = 'GENUS'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t WHERE t.parent_id = p.id AND t.`rank` = 'SPECIES' AND t.scientific_name = 'Portunus sanguinolentus'
  );

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家二级保护野生动物',
  'LC',
  '近海常见中大型齿鲸，常作为海洋生态监测和公众科普的重要代表物种。',
  '体色灰至深灰，吻部明显，背鳍镰刀状，体型流线。',
  '喜群游，常在近岸海域追逐鱼群和头足类，社会性较强。',
  '海湾、外海陆架边缘、岛礁周边水域。',
  '分布于全球温暖海域，中国南海和东海均有记录。',
  20.5612000,
  110.6125000,
  '雷州半岛外海与南海北部岛礁带',
  NULL,
  '中国海兽志；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Tursiops truncatus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家一级保护野生动物',
  'LC',
  '大型洄游须鲸，偶见于中国南部近海，是海洋高营养级生物的重要组成部分。',
  '胸鳍很长，尾鳍宽大，头部和胸鳍常可见白色花纹。',
  '季节性迁移明显，主要摄食磷虾、小型鱼类和浮游动物。',
  '外海、陆坡及远岸开阔海域。',
  '广泛分布于世界海洋，中国南海北部偶有观测记录。',
  19.8842000,
  111.2034000,
  '琼州海峡外缘与南海北部迁游通道',
  NULL,
  '中国鲸类研究资料；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Megaptera novaeangliae'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家二级保护野生动物',
  'CR',
  '典型近海大型鲨鱼，种群对过度捕捞和兼捕极为敏感。',
  '头部横向展开呈锤状，背鳍高大，体色灰褐。',
  '活动范围较广，幼体和亚成体常利用近岸湾区作为索饵或育幼场。',
  '近岸大陆架、海湾口门与暖水外海。',
  '分布于热带亚热带海域，中国南海和东海均有记录。',
  20.4268000,
  111.0173000,
  '南海北部大陆架外缘与湾口海域',
  NULL,
  '鲨鱼资源评估资料；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Sphyrna lewini'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '国家二级保护野生动物',
  'VU',
  '海草床和珊瑚礁周边常见的小型鱼类，对栖息地质量变化敏感。',
  '体形直立，尾部卷曲，头部呈马状，体表具骨板。',
  '游泳能力较弱，常附着海草或藻体，摄食小型甲壳类和浮游生物。',
  '海草床、藻场、珊瑚礁边缘和静水浅湾。',
  '分布于南海和华南近海岛礁海域。',
  20.4726000,
  110.7340000,
  '湛江近海海草床与浅水礁坪带',
  NULL,
  '中国海马资源调查；IUCN Red List',
  1
FROM taxon t
WHERE t.scientific_name = 'Hippocampus kuda'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '重要经济物种',
  'LC',
  '华南近海与河口常见鱼类，是沿海增殖放流和生态修复中的常见对象。',
  '体侧有不规则斑点，背部灰绿，体形修长。',
  '幼鱼常利用河口和海湾浅水区育成，成鱼偏肉食。',
  '河口、海湾、近岸岩礁与养殖区周边海域。',
  '广泛分布于中国沿海，在珠江口和雷州湾均较常见。',
  21.0284000,
  110.4067000,
  '雷州湾内湾与河口浅滩带',
  NULL,
  '中国近海鱼类志',
  1
FROM taxon t
WHERE t.scientific_name = 'Lateolabrax maculatus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '重要经济物种',
  'LC',
  '热带近海礁栖鱼类，常见于海湾、红树林边缘与珊瑚礁过渡带。',
  '体色红褐，成鱼尾鳍和各鳍常带红色，嘴大。',
  '幼鱼可进入河口和红树林水道，成鱼偏肉食，摄食甲壳类和小型鱼类。',
  '红树林、珊瑚礁、海湾口门和近岸礁区。',
  '分布于南海及热带西太平洋海域，中国南部沿海有记录。',
  20.3511000,
  110.6459000,
  '雷州半岛东岸红树林-礁坪复合生境',
  NULL,
  '中国热带海洋鱼类图鉴',
  1
FROM taxon t
WHERE t.scientific_name = 'Lutjanus argentimaculatus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '地方常见种',
  'LC',
  '暖水性礁栖鱼类，是近岸藻场和礁区食物网中的重要成员。',
  '体侧黄褐至灰褐，体形侧扁，背鳍棘强。',
  '常成小群活动，取食藻类及附着生物，幼鱼偏食浮游动物。',
  '海藻场、礁区、海湾口门及近岸岩礁区。',
  '分布于南海和东海暖水海域，广东近海较常见。',
  20.4185000,
  110.7812000,
  '湛江近海藻场和礁石混合底质区',
  NULL,
  '中国海洋鱼类生态资料',
  1
FROM taxon t
WHERE t.scientific_name = 'Siganus fuscescens'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '重要渔业资源种',
  'NE',
  '华南近海常见大型乌贼类，是近海渔业资源和头足类监测中的重要对象。',
  '胴部宽厚，外套膜有斑纹，内壳发达。',
  '昼夜垂直活动明显，摄食小型鱼虾蟹类，生长较快。',
  '近岸沙泥底、海湾口门和陆架浅海区。',
  '分布于南海北部、北部湾及东南沿海海域。',
  20.6477000,
  110.2923000,
  '雷州湾外海沙泥底和近岸陆架浅海区',
  NULL,
  '中国头足类研究资料',
  1
FROM taxon t
WHERE t.scientific_name = 'Sepia pharaonis'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO species (
  taxon_id, protection_level, iucn_status, description, morphology, habit, habitat, distribution,
  distribution_lat, distribution_lng, geo_range_text, video_url, reference_text, status
)
SELECT
  t.id,
  '重要养殖与生态修复物种',
  'NE',
  '珠江口和粤西河口常见大型牡蛎，对滤水净化和人工礁修复具有重要价值。',
  '壳体厚重，外形不规则，左壳固着，右壳盖状。',
  '以滤食浮游生物和有机颗粒为主，可形成牡蛎礁生境。',
  '河口咸淡水交汇区、潮间带和低盐海湾环境。',
  '主要分布于珠江口、粤西沿海及香港周边河口海域。',
  21.2043000,
  110.5841000,
  '粤西河口潮间带与低盐海湾',
  NULL,
  '中国牡蛎资源调查资料',
  1
FROM taxon t
WHERE t.scientific_name = 'Crassostrea hongkongensis'
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
  '常见近海游泳蟹类，生态位与三疣梭子蟹相近，是近岸底栖群落的重要成员。',
  '甲面具红褐色斑纹，步足发达，最后一对步足扁平。',
  '多夜间觅食，善游泳与埋沙，摄食小型底栖动物。',
  '近海沙泥底、岛礁外围和海湾外侧海域。',
  '分布于中国南海及东海南部海区。',
  20.5581000,
  110.9184000,
  '南海北部近岸沙泥底与岛礁外缘海域',
  NULL,
  '中国海洋甲壳动物图鉴',
  1
FROM taxon t
WHERE t.scientific_name = 'Portunus sanguinolentus'
  AND t.`rank` = 'SPECIES'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);
