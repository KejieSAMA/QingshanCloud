package com.x.cloudnext.server.orgsync;

public class OrgMirrorStats {

    private long units;
    private long persons;
    private long identities;
    private long roles;
    private long groups;
    private long memberships;
    private String capturedAt;

    public long getUnits() {
        return units;
    }

    public void setUnits(long units) {
        this.units = units;
    }

    public long getPersons() {
        return persons;
    }

    public void setPersons(long persons) {
        this.persons = persons;
    }

    public long getIdentities() {
        return identities;
    }

    public void setIdentities(long identities) {
        this.identities = identities;
    }

    public long getRoles() {
        return roles;
    }

    public void setRoles(long roles) {
        this.roles = roles;
    }

    public long getGroups() {
        return groups;
    }

    public void setGroups(long groups) {
        this.groups = groups;
    }

    public long getMemberships() {
        return memberships;
    }

    public void setMemberships(long memberships) {
        this.memberships = memberships;
    }

    public String getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(String capturedAt) {
        this.capturedAt = capturedAt;
    }
}
