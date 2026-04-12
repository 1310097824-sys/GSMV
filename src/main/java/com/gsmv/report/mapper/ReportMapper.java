package com.gsmv.report.mapper;

import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.report.dto.ObservationMapPoint;
import com.gsmv.report.dto.SpeciesDistributionPoint;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReportMapper {

    @Select("SELECT COUNT(*) FROM species WHERE status = 1")
    long countActiveSpecies();

    @Select("SELECT COUNT(*) FROM observation")
    long countObservations();

    @Select("SELECT COUNT(*) FROM ecosystem")
    long countEcosystems();

    @Select("SELECT COUNT(*) FROM sys_user WHERE status = 1")
    long countActiveUsers();

    @Select("SELECT COUNT(*) FROM observation WHERE observed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    long countRecentObservations();

    @Select("""
            SELECT COALESCE(NULLIF(protection_level, ''), '未分级') AS name, COUNT(*) AS value
            FROM species
            GROUP BY COALESCE(NULLIF(protection_level, ''), '未分级')
            ORDER BY value DESC, name ASC
            """)
    List<NameValuePoint> protectionLevelDistribution();

    @Select("""
            SELECT COALESCE(NULLIF(iucn_status, ''), '未评估') AS name, COUNT(*) AS value
            FROM species
            GROUP BY COALESCE(NULLIF(iucn_status, ''), '未评估')
            ORDER BY value DESC, name ASC
            """)
    List<NameValuePoint> iucnStatusDistribution();

    @Select("""
            SELECT COALESCE(NULLIF(phylum_taxon.chinese_name, ''), phylum_taxon.scientific_name, '未分类') AS name,
                   COUNT(*) AS value
            FROM species s
            JOIN taxon species_taxon ON species_taxon.id = s.taxon_id
            LEFT JOIN taxon genus_taxon ON genus_taxon.id = species_taxon.parent_id
            LEFT JOIN taxon family_taxon ON family_taxon.id = genus_taxon.parent_id
            LEFT JOIN taxon order_taxon ON order_taxon.id = family_taxon.parent_id
            LEFT JOIN taxon class_taxon ON class_taxon.id = order_taxon.parent_id
            LEFT JOIN taxon phylum_taxon ON phylum_taxon.id = class_taxon.parent_id
            GROUP BY COALESCE(NULLIF(phylum_taxon.chinese_name, ''), phylum_taxon.scientific_name, '未分类')
            ORDER BY value DESC, name ASC
            """)
    List<NameValuePoint> speciesPhylumDistribution();

    @Select("""
            SELECT COALESCE(NULLIF(class_taxon.chinese_name, ''), class_taxon.scientific_name, '未分类') AS name,
                   COUNT(*) AS value
            FROM species s
            JOIN taxon species_taxon ON species_taxon.id = s.taxon_id
            LEFT JOIN taxon genus_taxon ON genus_taxon.id = species_taxon.parent_id
            LEFT JOIN taxon family_taxon ON family_taxon.id = genus_taxon.parent_id
            LEFT JOIN taxon order_taxon ON order_taxon.id = family_taxon.parent_id
            LEFT JOIN taxon class_taxon ON class_taxon.id = order_taxon.parent_id
            GROUP BY COALESCE(NULLIF(class_taxon.chinese_name, ''), class_taxon.scientific_name, '未分类')
            ORDER BY value DESC, name ASC
            """)
    List<NameValuePoint> speciesClassDistribution();

    @Select("""
            SELECT DATE_FORMAT(observed_at, '%Y-%m-%d') AS name, COUNT(*) AS value
            FROM observation
            WHERE observed_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE_FORMAT(observed_at, '%Y-%m-%d')
            ORDER BY name
            """)
    List<NameValuePoint> observationTrend(@Param("days") int days);

    @Select("""
            SELECT COALESCE(NULLIF(u.display_name, ''), u.username) AS name, COUNT(o.id) AS value
            FROM observation o
            JOIN sys_user u ON u.id = o.observer_user_id
            WHERE o.observed_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY u.id, COALESCE(NULLIF(u.display_name, ''), u.username)
            ORDER BY value DESC, name ASC
            """)
    List<NameValuePoint> observationActivityByUser(@Param("days") int days);

    @Select("""
            SELECT
                e.id AS ecosystem_id,
                e.name AS ecosystem_name,
                e.type AS ecosystem_type,
                COUNT(DISTINCT o.id) AS observation_count,
                COUNT(DISTINCT os.species_id) AS species_count
            FROM ecosystem e
            LEFT JOIN observation o ON o.ecosystem_id = e.id
            LEFT JOIN observation_species os ON os.observation_id = o.id
            GROUP BY e.id, e.name, e.type
            ORDER BY observation_count DESC, species_count DESC, e.name ASC
            """)
    List<EcosystemAnalyticsPoint> ecosystemAnalytics();

    @Select("""
            SELECT
                s.id AS species_id,
                t.scientific_name,
                t.chinese_name,
                s.distribution_lat AS location_lat,
                s.distribution_lng AS location_lng,
                s.geo_range_text,
                s.protection_level,
                s.iucn_status
            FROM species s
            JOIN taxon t ON t.id = s.taxon_id
            WHERE s.status = 1
              AND s.distribution_lat IS NOT NULL
              AND s.distribution_lng IS NOT NULL
            ORDER BY t.scientific_name ASC
            """)
    List<SpeciesDistributionPoint> speciesDistributionPoints();

    @Select("""
            SELECT
                o.id AS observation_id,
                e.name AS ecosystem_name,
                COALESCE(NULLIF(u.display_name, ''), u.username) AS observer_name,
                o.observed_at,
                o.location_lat,
                o.location_lng,
                o.location_name,
                COUNT(DISTINCT os.species_id) AS species_count,
                o.note
            FROM observation o
            JOIN ecosystem e ON e.id = o.ecosystem_id
            JOIN sys_user u ON u.id = o.observer_user_id
            LEFT JOIN observation_species os ON os.observation_id = o.id
            GROUP BY o.id, e.name, COALESCE(NULLIF(u.display_name, ''), u.username), o.observed_at,
                     o.location_lat, o.location_lng, o.location_name, o.note
            ORDER BY o.observed_at DESC
            """)
    List<ObservationMapPoint> observationMapPoints();
}
