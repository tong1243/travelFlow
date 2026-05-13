package com.example.demo.rag.security;

import com.example.demo.rag.RagException;
import com.example.demo.rag.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
/**
 * JwtTokenService类。
 * 该类型负责认证授权与访问控制，保障系统安全边界。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class JwtTokenService {

    private final AppSecurityProperties securityProperties;
    private final SecretKey signingKey;

    /**
     * 构造并初始化 JwtTokenService 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param securityProperties 输入参数 securityProperties，用于参与本次处理流程。
     */
    public JwtTokenService(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = buildSigningKey(securityProperties.getJwtSecret());
    }

    /**
     * 执行 createToken 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(Math.max(300, securityProperties.getJwtExpireSeconds()));

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 执行 isTokenValid 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @param user 输入参数 user，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    public boolean isTokenValid(String token, AuthenticatedUser user) {
        try {
            Claims claims = parseClaims(token);
            String username = claims.getSubject();
            return Objects.equals(username, user.getUsername()) && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 执行 extractUsername 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 执行 extractUserId 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public Long extractUserId(String token) {
        Object uid = parseClaims(token).get("uid");
        if (uid instanceof Integer intUid) {
            return intUid.longValue();
        }
        if (uid instanceof Long longUid) {
            return longUid;
        }
        if (uid instanceof String stringUid && !stringUid.isBlank()) {
            return Long.parseLong(stringUid);
        }
        throw new RagException("令牌中缺少用户标识。");
    }

    /**
     * 获取 ExpireSeconds 字段值。
     * 该方法提供只读访问入口，减少调用方直接操作内部状态的风险。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    public long getExpireSeconds() {
        return Math.max(300, securityProperties.getJwtExpireSeconds());
    }

    /**
     * 执行 parseClaims 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param token 输入参数 token，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 执行 isExpired 条件判断。
     * 该方法返回布尔判定结果，用于上层流程分支控制和策略选择。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param claims 输入参数 claims，用于参与本次处理流程。
     * @return 返回判断结果：`true` 表示条件成立，`false` 表示条件不成立。
     */
    private boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.before(new Date());
    }

    /**
     * 执行 buildSigningKey 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param secret 输入参数 secret，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new RagException("安全密钥配置不能为空。");
        }
        String normalizedSecret = secret.trim();
        byte[] raw;
        try {
            raw = Decoders.BASE64.decode(normalizedSecret);
            if (raw.length < 32) {
                raw = digest(normalizedSecret);
            }
        } catch (RuntimeException ex) {
            raw = normalizedSecret.getBytes(StandardCharsets.UTF_8);
            if (raw.length < 32) {
                raw = digest(normalizedSecret);
            }
        }
        return Keys.hmacShaKeyFor(raw);
    }

    /**
     * 执行 digest 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于认证授权处理，确保访问链路符合安全策略。
     * @param value 输入参数 value，用于参与本次处理流程。
     * @return 返回当前步骤处理结果；无有效结果时返回实现约定的空值或默认值。
     */
    private byte[] digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new RagException("无法初始化令牌密钥。", ex);
        }
    }
}
