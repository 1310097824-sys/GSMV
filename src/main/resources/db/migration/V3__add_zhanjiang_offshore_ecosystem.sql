INSERT INTO ecosystem (name, type, description)
SELECT '湛江近海', 'OFFSHORE', '以湛江近海海域为核心的生态系统，覆盖近岸海域、海湾与典型海洋生境，便于统一开展物种观测与生态监测。'
WHERE NOT EXISTS (
  SELECT 1 FROM ecosystem WHERE name = '湛江近海'
);
