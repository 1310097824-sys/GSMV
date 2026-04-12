package com.gsmv.security;

import com.gsmv.config.JwtProperties;
import com.gsmv.user.model.SysUser;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(SysUser user, List<String> roleCodes, List<String> permissionCodes) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenTtlMinutes() * 60);
        Set<String> authorities = new LinkedHashSet<>(permissionCodes);
        roleCodes.forEach(role -> authorities.add("ROLE_" + role));

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("displayName", user.getDisplayName())
                .claim("roles", roleCodes)
                .claim("authorities", List.copyOf(authorities))
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claimsSet)).getTokenValue();
    }

    public long getTtlSeconds() {
        return jwtProperties.accessTokenTtlMinutes() * 60;
    }
}
