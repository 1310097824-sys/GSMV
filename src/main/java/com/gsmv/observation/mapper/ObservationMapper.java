package com.gsmv.observation.mapper;

import com.gsmv.observation.dto.ObservationSpeciesInput;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.model.Observation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ObservationMapper {

    @Select("""
            <script>
            SELECT
              o.id,
              o.ecosystem_id,
              e.name AS ecosystem_name,
              o.observer_user_id,
              u.display_name AS observer_name,
              o.observed_at,
              o.location_lat,
              o.location_lng,
              o.location_name,
              o.env_json,
              o.note,
              o.created_at
            FROM observation o
            JOIN ecosystem e ON e.id = o.ecosystem_id
            JOIN sys_user u ON u.id = o.observer_user_id
            <where>
              <if test='ecosystemId != null'>
                AND o.ecosystem_id = #{ecosystemId}
              </if>
              <if test='keyword != null and keyword != ""'>
                AND (
                  e.name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR o.location_name LIKE CONCAT('%', #{keyword}, '%')
                  OR o.note LIKE CONCAT('%', #{keyword}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM observation_species os
                    JOIN species s ON s.id = os.species_id
                    JOIN taxon t ON t.id = s.taxon_id
                    WHERE os.observation_id = o.id
                      AND (
                        t.scientific_name LIKE CONCAT('%', #{keyword}, '%')
                        OR t.chinese_name LIKE CONCAT('%', #{keyword}, '%')
                      )
                  )
                )
              </if>
              <if test='observedFrom != null'>
                AND o.observed_at &gt;= #{observedFrom}
              </if>
              <if test='observedTo != null'>
                AND o.observed_at &lt;= #{observedTo}
              </if>
            </where>
            ORDER BY o.observed_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ObservationView> findPage(
            @Param("ecosystemId") Long ecosystemId,
            @Param("keyword") String keyword,
            @Param("observedFrom") LocalDateTime observedFrom,
            @Param("observedTo") LocalDateTime observedTo,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM observation o
            JOIN ecosystem e ON e.id = o.ecosystem_id
            JOIN sys_user u ON u.id = o.observer_user_id
            <where>
              <if test='ecosystemId != null'>
                AND o.ecosystem_id = #{ecosystemId}
              </if>
              <if test='keyword != null and keyword != ""'>
                AND (
                  e.name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR o.location_name LIKE CONCAT('%', #{keyword}, '%')
                  OR o.note LIKE CONCAT('%', #{keyword}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM observation_species os
                    JOIN species s ON s.id = os.species_id
                    JOIN taxon t ON t.id = s.taxon_id
                    WHERE os.observation_id = o.id
                      AND (
                        t.scientific_name LIKE CONCAT('%', #{keyword}, '%')
                        OR t.chinese_name LIKE CONCAT('%', #{keyword}, '%')
                      )
                  )
                )
              </if>
              <if test='observedFrom != null'>
                AND o.observed_at &gt;= #{observedFrom}
              </if>
              <if test='observedTo != null'>
                AND o.observed_at &lt;= #{observedTo}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("ecosystemId") Long ecosystemId,
            @Param("keyword") String keyword,
            @Param("observedFrom") LocalDateTime observedFrom,
            @Param("observedTo") LocalDateTime observedTo
    );

    @Select("""
            SELECT
              o.id,
              o.ecosystem_id,
              e.name AS ecosystem_name,
              o.observer_user_id,
              u.display_name AS observer_name,
              o.observed_at,
              o.location_lat,
              o.location_lng,
              o.location_name,
              o.env_json,
              o.note,
              o.created_at
            FROM observation o
            JOIN ecosystem e ON e.id = o.ecosystem_id
            JOIN sys_user u ON u.id = o.observer_user_id
            WHERE o.id = #{id}
            """)
    ObservationView findViewById(Long id);

    @Select("SELECT * FROM observation WHERE id = #{id}")
    Observation findById(Long id);

    @Insert("""
            INSERT INTO observation (
              ecosystem_id, observer_user_id, observed_at, location_lat, location_lng,
              location_point, location_name, env_json, note
            ) VALUES (
              #{ecosystemId}, #{observerUserId}, #{observedAt}, #{locationLat}, #{locationLng},
              ST_SRID(Point(#{locationLng}, #{locationLat}), 4326), #{locationName}, #{envJson}, #{note}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Observation observation);

    @Insert("""
            INSERT INTO observation (
              id, ecosystem_id, observer_user_id, observed_at, location_lat, location_lng,
              location_point, location_name, env_json, note
            ) VALUES (
              #{id}, #{ecosystemId}, #{observerUserId}, #{observedAt}, #{locationLat}, #{locationLng},
              ST_SRID(Point(#{locationLng}, #{locationLat}), 4326), #{locationName}, #{envJson}, #{note}
            )
            """)
    void insertWithId(Observation observation);

    @Update("""
            UPDATE observation
            SET ecosystem_id = #{ecosystemId},
                observer_user_id = #{observerUserId},
                observed_at = #{observedAt},
                location_lat = #{locationLat},
                location_lng = #{locationLng},
                location_point = ST_SRID(Point(#{locationLng}, #{locationLat}), 4326),
                location_name = #{locationName},
                env_json = #{envJson},
                note = #{note}
            WHERE id = #{id}
            """)
    void update(Observation observation);

    @Insert("""
            <script>
            INSERT INTO observation_species (observation_id, species_id, count_estimated, behavior, comment)
            VALUES
            <foreach collection='items' item='item' separator=','>
              (#{observationId}, #{item.speciesId}, #{item.countEstimated}, #{item.behavior}, #{item.comment})
            </foreach>
            </script>
            """)
    void insertSpeciesBatch(@Param("observationId") Long observationId, @Param("items") List<ObservationSpeciesInput> items);

    @Delete("DELETE FROM observation_species WHERE observation_id = #{observationId}")
    void deleteSpeciesByObservationId(Long observationId);

    @Delete("DELETE FROM observation WHERE id = #{id}")
    void deleteById(Long id);

    @Select("""
            SELECT
              os.species_id,
              t.scientific_name,
              t.chinese_name,
              s.status,
              os.count_estimated,
              os.behavior,
              os.comment
            FROM observation_species os
            JOIN species s ON s.id = os.species_id
            JOIN taxon t ON t.id = s.taxon_id
            WHERE os.observation_id = #{observationId}
            ORDER BY os.id
            """)
    List<ObservationSpeciesView> findSpeciesViews(Long observationId);
}
