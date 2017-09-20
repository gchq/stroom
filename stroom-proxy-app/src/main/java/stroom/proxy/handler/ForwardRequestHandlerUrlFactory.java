package stroom.proxy.handler;

import org.springframework.util.StringUtils;

/**
 * Handler class that forwards the request to a URL.
 */
public class ForwardRequestHandlerUrlFactory {
    private String forwardUrl = null;
    private String[] forwardUrlParts = null;
    private int createCount = 0;

    public String[] getForwardUrlParts() {
        if (forwardUrlParts == null && StringUtils.hasText(forwardUrl)) {
            forwardUrlParts = forwardUrl.split(",");
        }
        return forwardUrlParts;
    }

    public String getForwardUrlPart() {
        return getForwardUrlParts()[createCount++];
    }

    public void setForwardUrl(String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

}
