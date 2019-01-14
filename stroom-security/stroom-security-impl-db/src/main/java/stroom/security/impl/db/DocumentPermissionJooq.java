package stroom.security.impl.db;

import java.util.*;

public class DocumentPermissionJooq {
    private String docType;
    private String docUuid;
    private Map<String, Set<String>> permissions = new HashMap<>();

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocUuid() {
        return docUuid;
    }

    public void setDocUuid(String docUuid) {
        this.docUuid = docUuid;
    }

    public Map<String, Set<String>> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Set<String>> permissions) {
        this.permissions = permissions;
    }

    public Set<String> getPermissionForUser(final String userUuid) {
        return permissions.getOrDefault(userUuid, Collections.emptySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentPermissionJooq that = (DocumentPermissionJooq) o;
        return Objects.equals(docType, that.docType) &&
                Objects.equals(docUuid, that.docUuid) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docType, docUuid, permissions);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DocumentPermissionJooq{");
        sb.append("docType='").append(docType).append('\'');
        sb.append(", docUuid='").append(docUuid).append('\'');
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public static class Builder {
        private final DocumentPermissionJooq instance;
        private final Map<String, Set<String>> permissions = new HashMap<>();

        public Builder(final DocumentPermissionJooq instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new DocumentPermissionJooq());
        }

        public Builder docType(final String value) {
            instance.setDocType(value);
            return this;
        }

        public Builder docUuid(final String value) {
            instance.setDocUuid(value);
            return this;
        }

        public Builder permission(final String userUuid, final String permission) {
            permissions.computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
            return this;
        }

        public DocumentPermissionJooq build() {
            instance.setPermissions(permissions);
            return instance;
        }
    }
}
