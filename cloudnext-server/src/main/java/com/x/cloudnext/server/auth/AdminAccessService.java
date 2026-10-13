package com.x.cloudnext.server.auth;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.config.CloudNextProperties;

/**
 * Admin 访问鉴权（N+2.0）—— 把 /api/admin/* 从"完全无认证"提升到
 * "白名单 OA id 才能调用"。
 *
 * 设计要点：
 * - 白名单来自 {@code cloudnext.admin.person-oa-ids}（逗号分隔字符串），
 *   启动时一次性解析为 {@link Set}，运行期不会再读配置；
 * - 不在 PostConstruct 抛错（哪怕白名单为空也允许启动），因为部署时常见
 *   "先起服务再配 admin"的顺序；空白名单时所有 requireAdmin 都 403；
 * - {@link #isAdmin(AuthLoginResponse)} 用于前端 whoami 端点的轻量探测，
 *   不会抛异常；{@link #requireAdmin(String)} 是写操作鉴权入口，抛 403。
 */
@Service
public class AdminAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccessService.class);

    private final AuthSessionService authSessionService;
    private final CloudNextProperties properties;

    private Set<String> allowedPersonOaIds = Collections.emptySet();
    private Set<String> allowedPersonKeys = Collections.emptySet();

    public AdminAccessService(AuthSessionService authSessionService, CloudNextProperties properties) {
        this.authSessionService = authSessionService;
        this.properties = properties;
    }

    @PostConstruct
    void initAllowList() {
        this.allowedPersonKeys = parse(properties.getAdmin().getPersonKeys());
        String raw = properties.getAdmin().getPersonOaIds();
        if (!StringUtils.hasText(raw)) {
            if (!this.allowedPersonKeys.isEmpty()) {
                LOGGER.info("cloudnext canonical admin allow-list initialized: {} entries", this.allowedPersonKeys.size());
            } else {
            LOGGER.warn("cloudnext admin allow-list is empty; all /api/admin/* mutating endpoints will return 403");
            }
            this.allowedPersonOaIds = Collections.emptySet();
            return;
        }
        Set<String> set = new HashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        this.allowedPersonOaIds = Collections.unmodifiableSet(set);
        LOGGER.info("cloudnext admin allow-list initialized: {} entries", set.size());
    }

    private Set<String> parse(String raw) {
        if (!StringUtils.hasText(raw)) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (String value : raw.split(",")) if (StringUtils.hasText(value)) result.add(value.trim());
        return Collections.unmodifiableSet(result);
    }

    /** 判断当前 session 是否在白名单内；session 为 null 直接 false。 */
    public boolean isAdmin(AuthLoginResponse session) {
        if (session == null) {
            return false;
        }
        if (session.getRoleList() != null && session.getRoleList().stream().anyMatch("SYSTEM_ADMIN"::equalsIgnoreCase)) {
            return true;
        }
        String personKey = StringUtils.hasText(session.getPersonKey()) ? session.getPersonKey() : session.getPersonOaId();
        if (!StringUtils.hasText(personKey)) {
            return false;
        }
        if (!allowedPersonKeys.isEmpty()) return allowedPersonKeys.contains(personKey);
        return allowedPersonOaIds.contains(personKey);
    }

    /** 写操作 / 敏感读操作鉴权入口。会话无效抛 401，白名单不匹配抛 403。 */
    public AuthLoginResponse requireAdmin(String token) {
        AuthLoginResponse session = authSessionService.currentSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cloudnext admin permission required");
        }
        return session;
    }

    /** 暴露给 whoami 端点 — 当前 session 是否管理员（不抛错） */
    public boolean checkIsAdminByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        AuthLoginResponse session = authSessionService.currentSession(token);
        return isAdmin(session);
    }
}
