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
     * @return The unique identifier for this user or group.
     */
    String getSubjectId();

    /**
     * @return An optional, non-unique, more human friendly username for the user.
     * Will be null if this is a group or the IDP does not provide a preferred username
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
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

//    String getUserUuid();

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
     */
    static String buildCombinedName(final UserName userName) {
        final String name = userName.getSubjectId();
        final String displayName = userName.getDisplayName();
        if (displayName == null) {
            return name;
        } else if (Objects.equals(name, displayName)) {
            return displayName;
        } else {
            return displayName + " (" + name + ")";
        }
    }
}
