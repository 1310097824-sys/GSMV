package com.gsmv.user.mapper;

import com.gsmv.user.model.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole findById(Long id);

    @Select("SELECT * FROM sys_role WHERE code = #{code}")
    SysRole findByCode(String code);

    @Select("SELECT COUNT(*) FROM sys_role WHERE code = #{code} AND id <> #{id}")
    int countByCodeExceptId(@Param("code") String code, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countUsersByRoleId(Long roleId);

    @Insert("""
            INSERT INTO sys_role (code, name, description)
            VALUES (#{code}, #{name}, #{description})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertRole(SysRole role);

    @Update("""
            UPDATE sys_role
            SET code = #{code},
                name = #{name},
                description = #{description}
            WHERE id = #{id}
            """)
    void updateRole(SysRole role);

    @Delete("DELETE FROM sys_role WHERE id = #{id}")
    void deleteRole(Long id);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteRolePermissions(Long roleId);

    @Insert("""
            <script>
            INSERT INTO sys_role_permission (role_id, permission_id)
            SELECT #{roleId}, p.id
            FROM sys_permission p
            WHERE p.code IN
            <foreach collection='permissionCodes' item='permissionCode' open='(' separator=',' close=')'>
              #{permissionCode}
            </foreach>
            </script>
            """)
    void insertRolePermissionsByCodes(
            @Param("roleId") Long roleId,
            @Param("permissionCodes") List<String> permissionCodes
    );

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
