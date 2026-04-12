INSERT INTO sys_role (code, name, description)
SELECT 'ADMIN', '系统管理员', '系统全量管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'ADMIN');

INSERT INTO sys_role (code, name, description)
SELECT 'RESEARCHER', '科研人员', '负责物种与观测录入'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'RESEARCHER');

INSERT INTO sys_role (code, name, description)
SELECT 'VIEWER', '访客', '仅查看数据'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'VIEWER');

INSERT INTO sys_permission (code, name, description)
SELECT 'SPECIES_READ', '查看物种', '允许查看物种数据'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'SPECIES_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'SPECIES_WRITE', '维护物种', '允许新增或编辑物种'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'SPECIES_WRITE');

INSERT INTO sys_permission (code, name, description)
SELECT 'OBS_READ', '查看观测', '允许查看观测记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'OBS_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'OBS_WRITE', '维护观测', '允许新增或编辑观测记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'OBS_WRITE');

INSERT INTO sys_permission (code, name, description)
SELECT 'ECOSYSTEM_READ', '查看生态系统', '允许查看生态系统'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ECOSYSTEM_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'ECOSYSTEM_WRITE', '维护生态系统', '允许新增或编辑生态系统'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'ECOSYSTEM_WRITE');

INSERT INTO sys_permission (code, name, description)
SELECT 'USER_ADMIN', '用户管理', '允许维护用户和角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'USER_ADMIN');

INSERT INTO sys_permission (code, name, description)
SELECT 'AUDIT_READ', '审计查看', '允许查看审计日志'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'AUDIT_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'REPORT_READ', '报表查看', '允许查看统计报表'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'REPORT_READ');

INSERT INTO sys_permission (code, name, description)
SELECT 'MEDIA_WRITE', '附件上传', '允许上传媒体文件'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'MEDIA_WRITE');

INSERT INTO sys_permission (code, name, description)
SELECT 'MEDIA_READ', '附件查看', '允许查看附件元数据'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'MEDIA_READ');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'RESEARCHER'
  AND p.code IN ('SPECIES_READ', 'SPECIES_WRITE', 'OBS_READ', 'OBS_WRITE', 'ECOSYSTEM_READ', 'ECOSYSTEM_WRITE', 'REPORT_READ', 'MEDIA_READ', 'MEDIA_WRITE')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'VIEWER'
  AND p.code IN ('SPECIES_READ', 'OBS_READ', 'ECOSYSTEM_READ', 'REPORT_READ', 'MEDIA_READ')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT NULL, 'PHYLUM', 'Chordata', '脊索动物门'
WHERE NOT EXISTS (SELECT 1 FROM taxon WHERE parent_id IS NULL AND `rank` = 'PHYLUM' AND scientific_name = 'Chordata');

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'CLASS', 'Mammalia', '哺乳纲'
FROM taxon p
WHERE p.scientific_name = 'Chordata'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t
    WHERE t.`rank` = 'CLASS' AND t.scientific_name = 'Mammalia'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'ORDER', 'Cetacea', '鲸偶蹄目'
FROM taxon p
WHERE p.scientific_name = 'Mammalia'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t
    WHERE t.`rank` = 'ORDER' AND t.scientific_name = 'Cetacea'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'FAMILY', 'Delphinidae', '海豚科'
FROM taxon p
WHERE p.scientific_name = 'Cetacea'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t
    WHERE t.`rank` = 'FAMILY' AND t.scientific_name = 'Delphinidae'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'GENUS', 'Sousa', '驼背豚属'
FROM taxon p
WHERE p.scientific_name = 'Delphinidae'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t
    WHERE t.`rank` = 'GENUS' AND t.scientific_name = 'Sousa'
  );

INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
SELECT p.id, 'SPECIES', 'Sousa chinensis', '中华白海豚'
FROM taxon p
WHERE p.scientific_name = 'Sousa'
  AND NOT EXISTS (
    SELECT 1 FROM taxon t
    WHERE t.`rank` = 'SPECIES' AND t.scientific_name = 'Sousa chinensis'
  );

INSERT INTO species (taxon_id, protection_level, iucn_status, description, morphology, habitat, distribution, status)
SELECT t.id, '国家一级保护', 'VU', '典型近岸海洋哺乳动物，常见于河口与海湾。', '体色会随年龄变化，成年个体偏粉白。', '近海浅水区、河口、港湾。', '华南沿海海域。', 1
FROM taxon t
WHERE t.scientific_name = 'Sousa chinensis'
  AND NOT EXISTS (SELECT 1 FROM species s WHERE s.taxon_id = t.id);

INSERT INTO ecosystem (name, type, description)
SELECT '珠江口海湾', 'ESTUARY', '淡水与海水交汇的典型河口海湾生态系统'
WHERE NOT EXISTS (SELECT 1 FROM ecosystem WHERE name = '珠江口海湾');

INSERT INTO ecosystem (name, type, description)
SELECT '珊瑚礁保育区', 'REEF', '珊瑚礁与周边鱼类群落的重要保育区域'
WHERE NOT EXISTS (SELECT 1 FROM ecosystem WHERE name = '珊瑚礁保育区');
