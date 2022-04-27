package stroom.dashboard.impl;

public class ApplicationInstance {

    private final String uuid;
    private final String userId;
    private final long createTime;
    private final ActiveQueries activeQueries = new ActiveQueries();

    public ApplicationInstance(final String uuid,
                               final String userId,
                               final long createTime) {
        this.uuid = uuid;
        this.userId = userId;
        this.createTime = createTime;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUserId() {
        return userId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void destroy() {
        activeQueries.destroy();
    }

    public void keepAlive() {
        activeQueries.keepAlive();
    }

    public ActiveQueries getActiveQueries() {
        return activeQueries;
    }

    @Override
    public String toString() {
        return "ApplicationInstance{" +
                "uuid='" + uuid + '\'' +
                ", userId='" + userId + '\'' +
                ", createTime=" + createTime +
                ", activeQueries=" + activeQueries +
                '}';
    }
}
