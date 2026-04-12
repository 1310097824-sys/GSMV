package com.gsmv.ecosystem.mapper;

import com.gsmv.ecosystem.model.Ecosystem;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EcosystemMapper {

    @Select("""
            <script>
            SELECT *
            FROM ecosystem
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  name LIKE CONCAT('%', #{keyword}, '%')
                  OR type LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='type != null and type != ""'>
                AND type = #{type}
              </if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Ecosystem> findPage(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ecosystem
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  name LIKE CONCAT('%', #{keyword}, '%')
                  OR type LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='type != null and type != ""'>
                AND type = #{type}
              </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("type") String type);

    @Select("SELECT * FROM ecosystem WHERE id = #{id}")
    Ecosystem findById(Long id);

    @Select("SELECT * FROM ecosystem ORDER BY name")
    List<Ecosystem> findAll();

    @Insert("""
            INSERT INTO ecosystem (name, type, description)
            VALUES (#{name}, #{type}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Ecosystem ecosystem);

    @Update("""
            UPDATE ecosystem
            SET name = #{name},
                type = #{type},
                description = #{description}
            WHERE id = #{id}
            """)
    void update(Ecosystem ecosystem);

    @Delete("DELETE FROM ecosystem WHERE id = #{id}")
    void deleteById(Long id);

    @Select("SELECT COUNT(*) FROM observation WHERE ecosystem_id = #{id}")
    long countObservationReferences(Long id);
}
