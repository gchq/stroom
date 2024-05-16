package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

// This Iface allows us to use UserNameImpl and User interchangeably
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        defaultImpl = SimpleUserName.class)
public interface UserName extends HasAuditableUserIdentity {

    /**
     * <p>If this is a user then this is the unique identifier for the user on the
     * OpenIdConnect IDP, e.g. the subject. The value may be a UUID or a more human friendly form
     * depending on the IDP in use (internal/external).</p>
     *
     * <p>If {@code isGroup} is {@code true} then this is the unique name of the group.
     * A group name is defined by the user so is likely to be human friendly.
     * A user and a group can share the same name.</p>
     *
     * @return The unique identifier for this user or group.
     */
    String getSubjectId();

    /**
     * @return An optional, potentially non-unique, more human friendly username for the user.
     * Will be null if this is a group or the IDP does not provide a preferred username
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID. This value will be used for all the {@code (create|update)_user}
     * columns or if it is null, the subjectId will be used instead
     * (see {@link UserName#getUserIdentityForAudit()}.
     */
    String getDisplayName();

    /**
     * @return An optional, non-unique, full name in displayable form including all name parts,
     * possibly including titles and suffixes, ordered according to the End-User's locale and
     * preferences.
     * Will be null if this is a group or the IDP does not provide a full-name
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
     */
    String getFullName();

    /**
     * @return The stroom user UUID. May be null.
     */
    String getUuid();

    /**
     * @return Whether this identity represents a single user or a group of users.
     */
    boolean isGroup();

    /**
     * @return The user identity to be used in audit events, audit columns and for display in
     * the UI.
     */
    default String getUserIdentityForAudit() {
        return HasAuditableUserIdentity.fromUserNames(getSubjectId(), getDisplayName());
    }

    /**
     * Combine the name and displayName, only showing both if they are both present
     * and not equal. Useful for the User parts of the UI where showing both is helpful.
     * i.e.
     * <p>{@code '6798d3ca-c1a1-490e-a52e-132ade052468'} if there is no displayName</p>
     * <p>{@code 'admin'} if the displayName and subjectId are the same</p>
     * <p>{@code 'admin (6798d3ca-c1a1-490e-a52e-132ade052468)'} if they are different</p>
     */
    static String buildCombinedName(final UserName userName) {
        return GwtNullSafe.get(userName,
                userName2 -> buildCombinedName(userName2.getSubjectId(), userName2.getDisplayName()));
    }

    static String buildCombinedName(final String subjectId, final String displayName) {
        if (displayName == null) {
            return subjectId;
        } else if (Objects.equals(subjectId, displayName)) {
            return displayName;
        } else {
            return displayName + " (" + subjectId + ")";
        }
    }
}
