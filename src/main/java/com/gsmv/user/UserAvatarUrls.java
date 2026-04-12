package com.gsmv.user;

import com.gsmv.user.model.SysUser;
import java.time.ZoneOffset;

public final class UserAvatarUrls {

    public static final String DEFAULT_AVATAR_URL = "/default-avatar.jpg";

    private UserAvatarUrls() {
    }

    public static String resolve(SysUser user) {
        if (user.getAvatarMediaId() == null) {
            return DEFAULT_AVATAR_URL;
        }
        long version = user.getUpdatedAt() == null
                ? user.getAvatarMediaId()
                : user.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return "/api/v1/users/avatar/" + user.getId() + "?v=" + version;
    }
}
