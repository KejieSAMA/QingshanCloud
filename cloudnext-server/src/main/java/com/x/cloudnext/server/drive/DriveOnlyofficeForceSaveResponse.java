package com.x.cloudnext.server.drive;

public class DriveOnlyofficeForceSaveResponse {

    private boolean accepted;
    private String state;
    private String message;

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
