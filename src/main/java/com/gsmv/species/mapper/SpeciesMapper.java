package com.gsmv.species.mapper;

import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.model.Species;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpeciesMapper {

    @Select("""
            <script>
            SELECT
              s.id,
              s.taxon_id,
              t.`rank` AS `rank`,
              t.scientific_name,
              t.chinese_name,
              s.protection_level,
              s.iucn_status,
              s.description,
              s.morphology,
              s.habit,
              s.habitat,
              s.distribution,
              s.distribution_lat,
              s.distribution_lng,
              s.geo_range_text,
              s.video_url,
              s.reference_text,
              s.status,
              s.created_at,
              s.updated_at
            FROM species s
            JOIN taxon t ON t.id = s.taxon_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  t.scientific_name LIKE CONCAT('%', #{keyword}, '%')
                  OR t.chinese_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null'>
                AND s.status = #{status}
              </if>
              <if test='protectionLevel != null and protectionLevel != ""'>
                AND s.protection_level LIKE CONCAT('%', #{protectionLevel}, '%')
              </if>
              <if test='iucnStatus != null and iucnStatus != ""'>
                AND UPPER(s.iucn_status) LIKE CONCAT('%', UPPER(#{iucnStatus}), '%')
              </if>
              <if test='distributionKeyword != null and distributionKeyword != ""'>
                AND (
                  s.distribution LIKE CONCAT('%', #{distributionKeyword}, '%')
                  OR s.geo_range_text LIKE CONCAT('%', #{distributionKeyword}, '%')
                )
              </if>
              <if test='taxonIds != null and taxonIds.size() > 0'>
                AND s.taxon_id IN
                <foreach collection='taxonIds' item='taxonId' open='(' separator=',' close=')'>
                  #{taxonId}
                </foreach>
              </if>
            </where>
            ORDER BY s.updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SpeciesRow> findPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("protectionLevel") String protectionLevel,
            @Param("iucnStatus") String iucnStatus,
            @Param("distributionKeyword") String distributionKeyword,
            @Param("taxonIds") List<Long> taxonIds,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM species s
            JOIN taxon t ON t.id = s.taxon_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  t.scientific_name LIKE CONCAT('%', #{keyword}, '%')
                  OR t.chinese_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null'>
                AND s.status = #{status}
              </if>
              <if test='protectionLevel != null and protectionLevel != ""'>
                AND s.protection_level LIKE CONCAT('%', #{protectionLevel}, '%')
              </if>
              <if test='iucnStatus != null and iucnStatus != ""'>
                AND UPPER(s.iucn_status) LIKE CONCAT('%', UPPER(#{iucnStatus}), '%')
              </if>
              <if test='distributionKeyword != null and distributionKeyword != ""'>
                AND (
                  s.distribution LIKE CONCAT('%', #{distributionKeyword}, '%')
                  OR s.geo_range_text LIKE CONCAT('%', #{distributionKeyword}, '%')
                )
              </if>
              <if test='taxonIds != null and taxonIds.size() > 0'>
                AND s.taxon_id IN
                <foreach collection='taxonIds' item='taxonId' open='(' separator=',' close=')'>
                  #{taxonId}
                </foreach>
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("protectionLevel") String protectionLevel,
            @Param("iucnStatus") String iucnStatus,
            @Param("distributionKeyword") String distributionKeyword,
            @Param("taxonIds") List<Long> taxonIds
    );

    @Select("""
            SELECT
              s.id,
              s.taxon_id,
              t.`rank` AS `rank`,
              t.scientific_name,
              t.chinese_name,
              s.protection_level,
              s.iucn_status,
              s.description,
              s.morphology,
              s.habit,
              s.habitat,
              s.distribution,
              s.distribution_lat,
              s.distribution_lng,
              s.geo_range_text,
              s.video_url,
              s.reference_text,
              s.status,
              s.created_at,
              s.updated_at
            FROM species s
            JOIN taxon t ON t.id = s.taxon_id
            WHERE s.id = #{id}
            """)
    SpeciesRow findRowById(Long id);

    @Select("""
            SELECT
              s.id,
              s.taxon_id,
              t.`rank` AS `rank`,
              t.scientific_name,
              t.chinese_name,
              s.protection_level,
              s.iucn_status,
              s.description,
              s.morphology,
              s.habit,
              s.habitat,
              s.distribution,
              s.distribution_lat,
              s.distribution_lng,
              s.geo_range_text,
              s.video_url,
              s.reference_text,
              s.status,
              s.created_at,
              s.updated_at
            FROM species s
            JOIN taxon t ON t.id = s.taxon_id
            WHERE s.id = #{id}
            """)
    SpeciesRow findViewById(Long id);

    @Select("SELECT * FROM species WHERE id = #{id}")
    Species findById(Long id);

    @Insert("""
            INSERT INTO species (
              taxon_id, protection_level, iucn_status, description,
              morphology, habit, habitat, distribution, distribution_lat, distribution_lng,
              geo_range_text, video_url, reference_text, status
            ) VALUES (
              #{taxonId}, #{protectionLevel}, #{iucnStatus}, #{description},
              #{morphology}, #{habit}, #{habitat}, #{distribution}, #{distributionLat}, #{distributionLng},
              #{geoRangeText}, #{videoUrl}, #{referenceText}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Species species);

    @Insert("""
            INSERT INTO species (
              id, taxon_id, protection_level, iucn_status, description,
              morphology, habit, habitat, distribution, distribution_lat, distribution_lng,
              geo_range_text, video_url, reference_text, status
            ) VALUES (
              #{id}, #{taxonId}, #{protectionLevel}, #{iucnStatus}, #{description},
              #{morphology}, #{habit}, #{habitat}, #{distribution}, #{distributionLat}, #{distributionLng},
              #{geoRangeText}, #{videoUrl}, #{referenceText}, #{status}
            )
            """)
    void insertWithId(Species species);

    @Update("""
            UPDATE species
            SET taxon_id = #{taxonId},
                protection_level = #{protectionLevel},
                iucn_status = #{iucnStatus},
                description = #{description},
                morphology = #{morphology},
                habit = #{habit},
                habitat = #{habitat},
                distribution = #{distribution},
                distribution_lat = #{distributionLat},
                distribution_lng = #{distributionLng},
                geo_range_text = #{geoRangeText},
                video_url = #{videoUrl},
                reference_text = #{referenceText},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void update(Species species);

    @Delete("DELETE FROM species WHERE id = #{id}")
    void deleteById(Long id);

    @Select("SELECT COUNT(*) FROM observation_species WHERE species_id = #{speciesId}")
    long countObservationReferences(Long speciesId);
}
