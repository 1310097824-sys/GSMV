package com.gsmv.security;

import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<CurrentUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        Set<String> granted = authorities == null ? Set.of() : new LinkedHashSet<>(authorities);
        Long userId = jwt.getClaim("userId");
        return Optional.of(new CurrentUser(
                userId,
                jwt.getSubject(),
                jwt.getClaimAsString("displayName"),
                granted
        ));
    }

    public static CurrentUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录", HttpStatus.UNAUTHORIZED));
    }
}
