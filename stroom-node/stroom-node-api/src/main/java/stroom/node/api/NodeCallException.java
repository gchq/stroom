package stroom.node.api;

import stroom.util.logging.LogUtil;

public class NodeCallException extends RuntimeException {
    private final String nodeName;
    private final String url;

    public NodeCallException(final String nodeName, final String url, final String msg) {
        super(msg);
        this.nodeName = nodeName;
        this.url = url;
    }

    public NodeCallException(final String nodeName, final String url, final Throwable throwable) {
        super(LogUtil.message("Unable to connect to node {} at url {}: {}",
            nodeName, url, throwable.getMessage()), throwable);
        this.nodeName = nodeName;
        this.url = url;
    }

    public NodeCallException(final String nodeName, final String url, final String msg, final Throwable throwable) {
        super(msg, throwable);
        this.nodeName = nodeName;
        this.url = url;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
