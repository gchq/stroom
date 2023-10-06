package stroom.security.shared;

import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonPropertyOrder({"docUuid", "users", "groups", "permissions"})
@JsonInclude(Include.NON_NULL)
public class DocumentPermissions {

    @JsonProperty
    private final String docUuid;
    // TODO: 27/07/2023 We could change both of these to hold UserName instead of User as
    //  we only need the the userUuid/displayName/subjectId
    @JsonProperty
    private final List<User> users;
    @JsonProperty
    private final List<User> groups;

    @JsonProperty
    private final Map<String, Set<String>> permissions;

    @JsonCreator
    public DocumentPermissions(@JsonProperty("docUuid") final String docUuid,
                               @JsonProperty("users") final List<User> users,
                               @JsonProperty("groups") final List<User> groups,
                               @JsonProperty("permissions") final Map<String, Set<String>> permissions) {
        this.docUuid = docUuid;
        this.users = users;
        this.groups = groups;
        this.permissions = permissions;
    }

    public String getDocUuid() {
        return docUuid;
    }

    public List<User> getUsers() {
        return users;
    }

    public List<User> getGroups() {
        return groups;
    }

    /**
     * @return Map of user/group stroom user uuid to a set of held permissions
     */
    public Map<String, Set<String>> getPermissions() {
        return new HashMap<>(permissions);
    }

    public void addPermission(final String userUuid, final String permission) {
        permissions.computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
    }

    public void removePermission(final String userUuid, final String permission) {
        final Set<String> perms = permissions.get(userUuid);
        if (perms != null) {
            perms.remove(permission);
        }
    }

    /**
     * @return The set of users with Owner permission
     */
    @JsonIgnore
    public Set<User> getOwners() {
        return permissions.entrySet()
                .stream()
                .filter(entry -> GwtNullSafe.set(entry.getValue()).contains(DocumentPermissionNames.OWNER))
                .map(Entry::getKey)
                .map(userUuid ->
                        Stream.concat(users.stream(), groups.stream())
                                .filter(user -> userUuid.equals(user.getUuid()))
                                .findFirst()
                                .orElseThrow(() ->
                                        new RuntimeException("User with uuid " + userUuid + " not in lists")))
                .collect(Collectors.toSet());
    }

    public boolean containsUserOrGroup(final String uuid, final boolean isGroup) {
        return isGroup
                ? containsGroup(uuid)
                : containsUser(uuid);
    }

    public boolean containsUser(final String userUuid) {
        return users.stream()
                .map(User::getUuid)
                .anyMatch(uuid -> Objects.equals(uuid, userUuid));
    }

    public boolean containsGroup(final String groupUuid) {
        return groups.stream()
                .map(User::getUuid)
                .anyMatch(uuid -> Objects.equals(uuid, groupUuid));
    }

    public Set<String> getPermissionsForUser(final String userUuid) {
        return new HashSet<>(permissions.getOrDefault(userUuid, Collections.emptySet()));
    }

    public void addUser(final User user, final boolean isGroup) {
        if (isGroup) {
            groups.add(user);
        } else {
            users.add(user);
        }
    }

    public void removeUser(final User user) {
        if (user.isGroup()) {
            groups.remove(user);
        } else {
            users.remove(user);
        }
        permissions.remove(user.getUuid());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocumentPermissions that = (DocumentPermissions) o;
        return Objects.equals(docUuid, that.docUuid) &&
                Objects.equals(users, that.users) &&
                Objects.equals(groups, that.groups) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docUuid, users, groups, permissions);
    }

    @Override
    public String toString() {
        return "DocumentPermissions{" +
                "docUuid='" + docUuid + '\'' +
                ", users=" + users +
                ", groups=" + groups +
                ", permissions=\n" + permsMapToStr(permissions) +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static String permsMapToStr(final Map<String, Set<String>> perms) {
        return GwtNullSafe.map(perms)
                .entrySet()
                .stream()
                .map(entry -> {
                    final String userUuid = entry.getKey();
                    final String permStr = entry.getValue()
                            .stream()
                            .sorted()
                            .collect(Collectors.joining(", "));
                    return userUuid + " => [" + permStr + "]";
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * @return A non-null mutable deep copy of the supplied perms map
     */
    public static Map<String, Set<String>> copyPermsMap(final Map<String, Set<String>> perms) {
        return GwtNullSafe.map(perms)
                .entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        new HashSet<>(GwtNullSafe.set(entry.getValue()))))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String docUuid;
        private List<User> users;
        private List<User> groups;
        private Map<String, Set<String>> permissions = new HashMap<>();

        private Builder() {
        }

        private Builder(final DocumentPermissions documentPermissions) {
            this.docUuid = documentPermissions.docUuid;
            this.users = documentPermissions.users;
            this.groups = documentPermissions.groups;
            this.permissions = documentPermissions.permissions;
        }

        public Builder docUuid(final String value) {
            docUuid = value;
            return this;
        }

        public Builder users(final List<User> value) {
            users = value;
            return this;
        }

        public Builder groups(final List<User> value) {
            groups = value;
            return this;
        }

        public Builder permission(final String userUuid, final String permission) {
            permissions.computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
            return this;
        }

        public Builder permissions(final Map<String, Set<String>> permissions) {
            this.permissions = permissions;
            return this;
        }

        public DocumentPermissions build() {
            return new DocumentPermissions(docUuid, users, groups, permissions);
        }
    }
}
