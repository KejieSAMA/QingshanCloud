package com.x.cloudnext.server.form;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Public codes/messages only; never expose upstream responses or ticket values. */
public class FormSsoException extends ResponseStatusException {
    private final String code;
    public FormSsoException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }
    public String getCode() { return code; }
    public static FormSsoException pending() {
        return new FormSsoException(HttpStatus.CONFLICT, "DIRECTORY_SYNC_PENDING", "人员正在同步，请稍后重新免登。");
    }
    public static FormSsoException syncFailed() {
        return new FormSsoException(HttpStatus.SERVICE_UNAVAILABLE, "DIRECTORY_SYNC_FAILED", "人员同步失败，请稍后重试或联系管理员。");
    }
    public static FormSsoException classify(ResponseStatusException failure) {
        if (failure instanceof FormSsoException) return (FormSsoException) failure;
        HttpStatus status = failure.getStatus();
        if (status == HttpStatus.UNAUTHORIZED) return new FormSsoException(status, "TICKET_INVALID", "免登票据已过期或失效，请重新免登。");
        if (status == HttpStatus.PRECONDITION_REQUIRED) return new FormSsoException(status, "PASSWORD_CHANGE_REQUIRED", "请先返回 OA 修改密码，再进入云盘。");
        if (status == HttpStatus.FORBIDDEN) return new FormSsoException(status, "IDENTITY_UNAVAILABLE", "身份不可用，请联系管理员核对。");
        if (status == HttpStatus.BAD_REQUEST) return new FormSsoException(status, "SSO_REQUEST_INVALID", "免登请求无效，请重新从 OA 进入云盘。");
        return new FormSsoException(status, "SSO_UNAVAILABLE", "免登服务暂不可用，请稍后重试。");
    }
}
