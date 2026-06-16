package com.gsmv.user.mapper;

import com.gsmv.user.model.SysPermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper {

    @Select("SELECT * FROM sys_permission ORDER BY code")
    List<SysPermission> findAll();

    @Select("""
            SELECT DISTINCT p.code
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY p.code
            """)
    List<String> findPermissionCodesByUserId(Long userId);

    @Select("""
            SELECT p.code
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.code
            """)
    List<String> findPermissionCodesByRoleId(Long roleId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_permission
            WHERE code IN
            <foreach collection='permissionCodes' item='permissionCode' open='(' separator=',' close=')'>
              #{permissionCode}
            </foreach>
            </script>
            """)
    int countByCodes(@Param("permissionCodes") List<String> permissionCodes);
}
