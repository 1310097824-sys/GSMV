package com.gsmv.species.mapper;

import com.gsmv.species.model.Taxon;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaxonMapper {

    @Select("SELECT id, parent_id, `rank`, scientific_name, chinese_name, created_at FROM taxon ORDER BY parent_id, `rank`, scientific_name")
    List<Taxon> findAll();

    @Select("SELECT id, parent_id, `rank`, scientific_name, chinese_name, created_at FROM taxon WHERE id = #{id}")
    Taxon findById(Long id);

    @Select("""
            <script>
            SELECT id, parent_id, `rank`, scientific_name, chinese_name, created_at
            FROM taxon
            WHERE `rank` = #{rank}
              AND scientific_name = #{scientificName}
              <choose>
                <when test='parentId != null'>
                  AND parent_id = #{parentId}
                </when>
                <otherwise>
                  AND parent_id IS NULL
                </otherwise>
              </choose>
            LIMIT 1
            </script>
            """)
    Taxon findByParentAndRankAndScientificName(
            @Param("parentId") Long parentId,
            @Param("rank") String rank,
            @Param("scientificName") String scientificName
    );

    @Insert("""
            INSERT INTO taxon (parent_id, `rank`, scientific_name, chinese_name)
            VALUES (#{parentId}, #{rank}, #{scientificName}, #{chineseName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Taxon taxon);

    @Update("""
            UPDATE taxon
            SET chinese_name = #{chineseName}
            WHERE id = #{id}
            """)
    void updateChineseName(@Param("id") Long id, @Param("chineseName") String chineseName);
}
