package stroom.util.shared;

public interface HasAuditInfoSetters {

    void setCreateTimeMs(Long createTimeMs);

    void setCreateUser(String createUser);

    void setUpdateTimeMs(Long updateTimeMs);

    void setUpdateUser(String updateUser);
}
