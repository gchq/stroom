package stroom.cluster.api;

import stroom.util.logging.LogUtil;

public class ClusterCallException extends RuntimeException {

    private final ClusterMember member;
    private final String url;

    public ClusterCallException(final ClusterMember member, final String url, final Throwable throwable) {
        super(LogUtil.message("Unable to connect to member {} at url {}: {}",
                member, url, throwable.getMessage()), throwable);
        this.member = member;
        this.url = url;
    }

    public ClusterMember getMember() {
        return member;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
