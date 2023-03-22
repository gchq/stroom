package stroom.util.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

/**
 * Pojo for representing enough info to identify a user
 */
@JsonInclude(Include.NON_NULL)
public class UserName implements Comparable<UserName>, HasAuditableUserIdentity {
    private static final Comparator<UserName> COMPARATOR = Comparator.comparing(UserName::getName);

    @JsonProperty
    private String name;
    @JsonProperty
    private String displayName;
    @JsonProperty
    private String fullName;

    @JsonIgnore
    private final String combinedName;

    @JsonCreator
    public UserName(@JsonProperty("name") final String name,
                    @JsonProperty("displayName") final String displayName,
                    @JsonProperty("fullName") final String fullName) {
        if (name == null || name.trim().length() == 0) {
            throw new RuntimeException("User unique identity must have a value");
        }
        this.name = Objects.requireNonNull(name);
        this.displayName = displayName;
        this.fullName = fullName;

        // This logic is replicated in UserIdentity
        if (displayName == null) {
            this.combinedName = name;
        } else if (Objects.equals(name, displayName)) {
            this.combinedName = displayName;
        } else {
            this.combinedName = displayName + " (" + name + ")";
        }
    }

    public UserName(final String name) {
        this(name, null, null);
    }

    /**
     * <p>If this is a user then {@code id} is the unique identifier for the user on the
     * OpenIdConnect IDP, e.g. the subject. The value may be a UUID or a more human friendly form
     * depending on the IDP in use (internal/external).</p>
     *
     * <p>If {@code isGroup} is {@code true} then this is the unique name of the group.
     * A group name is defined by the user so is likely to be human friendly.
     * A user and a group can share the same name.</p>
     * @return The unique identifier for this user or group.
     */
    public String getName() {
        return name;
    }

    /**
     * @return An optional, non-unique, more human friendly username for the user.
     * Will be null if this is a group or the IDP does not provide a preferred username
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return An optional, non-unique, full name in displayable form including all name parts,
     * possibly including titles and suffixes, ordered according to the End-User's locale and
     * preferences.
     * Will be null if this is a group or the IDP does not provide a full-name
     * or one has not been set for the user.
     * Intended for display purposes only or to aid in identifying the user where {@code name}
     * is an unfriendly UUID.
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * A value for use in the UI.
     * If there is no {@code displayName}, this will return {@code name}.
     * If {@code displayName} is the same as {@code name}, this will return {@code name}.
     * Else it will return 'displayName (name)'.
     */
    @JsonIgnore
    public String getCombinedName() {
        return combinedName;
    }

    @Override
    @JsonIgnore
    public String getUserIdentityForAudit() {
        return HasAuditableUserIdentity.fromUserNames(name, displayName);
    }

    @Override
    public String toString() {
        return combinedName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserName userName = (UserName) o;
        return name.equals(userName.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(final UserName o) {
        return COMPARATOR.compare(this, o);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String name;
        private String displayName;
        private String fullName;

        private Builder() {
        }

        private Builder(final UserName username) {
            this.name = username.name;
            this.displayName = username.getDisplayName();
            this.fullName = username.getFullName();
        }

        public Builder name(final String value) {
            name = value;
            return this;
        }

        public Builder displayName(final String value) {
            displayName = value;
            return this;
        }

        public Builder fullName(final String value) {
            fullName = value;
            return this;
        }

        public UserName build() {
            return new UserName(name, displayName, fullName);
        }
    }
}
