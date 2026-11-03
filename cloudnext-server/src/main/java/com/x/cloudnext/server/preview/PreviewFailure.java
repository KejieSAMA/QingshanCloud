package com.x.cloudnext.server.preview;

public class PreviewFailure extends RuntimeException {
    public final String code;
    public final boolean retryable;
    public PreviewFailure(String code, String message, boolean retryable) {
        super(message); this.code = code; this.retryable = retryable;
    }
}
