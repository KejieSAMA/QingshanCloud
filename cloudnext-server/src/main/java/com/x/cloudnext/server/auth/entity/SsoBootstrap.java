package com.x.cloudnext.server.auth.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="cloudnext_sso_bootstrap")
public class SsoBootstrap {
    @Id @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="session_token",nullable=false,length=128) private String sessionToken;
    @Column(name="return_path",nullable=false,length=1024) private String returnPath="/";
    @Column(name="expires_at",nullable=false,length=64) private String expiresAt;
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
    public String getSessionToken(){return sessionToken;} public void setSessionToken(String v){sessionToken=v;}
    public String getReturnPath(){return returnPath;} public void setReturnPath(String v){returnPath=v;}
    public String getExpiresAt(){return expiresAt;} public void setExpiresAt(String v){expiresAt=v;}
}
