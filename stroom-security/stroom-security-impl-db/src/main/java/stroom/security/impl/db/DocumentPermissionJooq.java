package stroom.security.impl.db;

import java.util.Objects;
import java.util.Set;

public class DocumentPermissionJooq {
    private long id;
    private byte version;
    private String userUuid;
    private String docType;
    private String docUuid;
    private Set<String> permissions;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public void setUserUuid(String userUuid) {
        this.userUuid = userUuid;
    }

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

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentPermissionJooq that = (DocumentPermissionJooq) o;
        return id == that.id &&
                version == that.version &&
                Objects.equals(userUuid, that.userUuid) &&
                Objects.equals(docType, that.docType) &&
                Objects.equals(docUuid, that.docUuid) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, userUuid, docType, docUuid, permissions);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DocumentPermissionJooq{");
        sb.append("id=").append(id);
        sb.append(", version=").append(version);
        sb.append(", userUuid='").append(userUuid).append('\'');
        sb.append(", docType='").append(docType).append('\'');
        sb.append(", docUuid='").append(docUuid).append('\'');
        sb.append(", permissions='").append(permissions).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
