package com.x.cloudnext.server.drive;

/**
 * Public link permission (iteration two, design §"迭代二：Public Link 闭环 / 实体").
 *
 * <p>Fixed to two values for the first phase per requirement 6.3:
 * <ul>
 *   <li>{@link #PREVIEW} — viewer may only preview the target file.</li>
 *   <li>{@link #DOWNLOAD} — viewer may both preview and download the target.</li>
 * </ul>
 *
 * <p>Stored on {@code DrivePublicLink} via {@code @Enumerated(EnumType.STRING)}
 * so the on-disk value is the literal enum name.
 */
public enum PublicLinkPermission {

    PREVIEW,
    DOWNLOAD
}
