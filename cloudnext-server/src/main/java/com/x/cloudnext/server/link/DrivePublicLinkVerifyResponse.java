package com.x.cloudnext.server.link;

/**
 * Response body for {@code POST /public/links/{token}/verify} (iteration
 * two, Requirement 8.3 — design §"DrivePublicLinkService" accessTicket).
 *
 * <p>Carries only the freshly-issued {@code accessTicket} string. The
 * caller forwards this verbatim to subsequent {@code preview} / {@code
 * download} calls via {@code ?accessTicket=...} or the
 * {@code X-CloudNext-Public-Ticket} header. The server treats it as an
 * opaque, signed bearer credential — its internal payload format
 * ({@code token.exp.iat} HMAC-signed) lives in
 * {@link PublicLinkSessionService} and intentionally does not leak into
 * this DTO.
 *
 * <p>Per Requirement 8.4, this response is only ever produced after the
 * supplied password matches the stored hash (or the link has no password
 * configured at all). It carries no information about the link's
 * permission, expiry, or download budget — those remain on
 * {@link DrivePublicLinkMetaResponse}.
 */
public class DrivePublicLinkVerifyResponse {

    private String accessTicket;

    public DrivePublicLinkVerifyResponse() {
    }

    public DrivePublicLinkVerifyResponse(String accessTicket) {
        this.accessTicket = accessTicket;
    }

    public String getAccessTicket() {
        return accessTicket;
    }

    public void setAccessTicket(String accessTicket) {
        this.accessTicket = accessTicket;
    }
}
