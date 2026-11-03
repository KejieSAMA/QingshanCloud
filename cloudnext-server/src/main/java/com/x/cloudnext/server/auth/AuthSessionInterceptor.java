package com.x.cloudnext.server.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthSessionInterceptor implements HandlerInterceptor {

    private static final String TOKEN_HEADER = "X-CloudNext-Token";

    private final AuthSessionService authSessionService;

    public AuthSessionInterceptor(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(TOKEN_HEADER);
        com.x.cloudnext.server.auth.AuthLoginResponse session = authSessionService.currentSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        String requestedDrive = request.getHeader("X-CloudNext-Drive");
        if (session.getDrive() != null && !session.getDrive().isDefault() && requestedDrive == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DRIVE_CONTEXT_REQUIRED");
        }
        if (requestedDrive != null && (session.getDrive() == null || !requestedDrive.equals(session.getDrive().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "drive session does not match current page");
        }
        return true;
    }
}
