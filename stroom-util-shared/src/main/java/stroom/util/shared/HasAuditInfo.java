package stroom.util.shared;

public interface HasAuditInfo {

    Long getCreateTimeMs();

    void setCreateTimeMs(Long createTimeMs);

    /**
     * @return The user display name for audit purposes of the user that created the record.
     * If the user has no display name then
     * their subject ID will be returned instead. It should NOT be used for anything other than
     * display or audit purposes.
     */
    String getCreateUser();

    void setCreateUser(String createUser);

    Long getUpdateTimeMs();

    void setUpdateTimeMs(Long updateTimeMs);

    /**
     * @return The user display name for audit purposes of the user that last updated the record.
     * If the user has no display name then
     * their subject ID will be returned instead. It should NOT be used for anything other than
     * display or audit purposes.
     */
    String getUpdateUser();

    void setUpdateUser(String updateUser);
}
