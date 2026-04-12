package com.gsmv.user.mapper;

import com.gsmv.user.model.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {

    @Select("""
            SELECT r.*
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<SysRole> findRolesByUserId(Long userId);

    @Select("""
            SELECT r.code
            FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.code
            """)
    List<String> findRoleCodesByUserId(Long userId);

    @Select("SELECT * FROM sys_role ORDER BY id")
    List<SysRole> findAll();

    @Select("SELECT * FROM sys_role WHERE code = #{code}")
    SysRole findByCode(String code);

    @Insert("""
            <script>
            INSERT INTO sys_user_role (user_id, role_id)
            VALUES
            <foreach collection='roleIds' item='roleId' separator=','>
              (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    void insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(Long userId);
}
