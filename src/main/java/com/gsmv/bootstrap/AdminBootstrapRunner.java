package com.gsmv.bootstrap;

import com.gsmv.config.AdminProperties;
import com.gsmv.user.mapper.RoleMapper;
import com.gsmv.user.mapper.UserMapper;
import com.gsmv.user.model.SysRole;
import com.gsmv.user.model.SysUser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminProperties adminProperties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            AdminProperties adminProperties,
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.adminProperties = adminProperties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.enabled()) {
            return;
        }
        if (userMapper.findByUsername(adminProperties.username()) != null) {
            return;
        }
        SysRole adminRole = roleMapper.findByCode("ADMIN");
        if (adminRole == null) {
            log.warn("ADMIN role is missing, skip bootstrap admin creation.");
            return;
        }

        SysUser user = new SysUser();
        user.setUsername(adminProperties.username());
        user.setDisplayName(adminProperties.displayName());
        user.setPasswordHash(passwordEncoder.encode(adminProperties.bootstrapPassword()));
        user.setStatus(1);
        userMapper.insert(user);
        roleMapper.insertUserRoles(user.getId(), List.of(adminRole.getId()));
        log.info("Bootstrap admin created. username={}, password={}", adminProperties.username(), adminProperties.bootstrapPassword());
    }
}
