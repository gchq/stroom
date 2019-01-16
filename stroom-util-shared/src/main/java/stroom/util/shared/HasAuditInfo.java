package stroom.util.shared;

public interface HasAuditInfo {
    Long getCreateTimeMs();

    void setCreateTimeMs(Long createTimeMs);

    String getCreateUser();

    void setCreateUser(String createUser);

    Long getUpdateTimeMs();

    void setUpdateTimeMs(Long updateTimeMs);

    String getUpdateUser();

    void setUpdateUser(String updateUser);
}
