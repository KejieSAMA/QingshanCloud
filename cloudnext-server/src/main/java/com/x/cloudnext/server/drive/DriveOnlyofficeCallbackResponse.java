package com.x.cloudnext.server.drive;

public class DriveOnlyofficeCallbackResponse {

    private int error;

    public DriveOnlyofficeCallbackResponse() {
    }

    public DriveOnlyofficeCallbackResponse(int error) {
        this.error = error;
    }

    public int getError() {
        return error;
    }

    public void setError(int error) {
        this.error = error;
    }

    public static DriveOnlyofficeCallbackResponse ok() {
        return new DriveOnlyofficeCallbackResponse(0);
    }

    public static DriveOnlyofficeCallbackResponse failed() {
        return new DriveOnlyofficeCallbackResponse(1);
    }
}
