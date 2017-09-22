package stroom.proxy.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * Handler class that forwards the request to a URL.
 */
public class ForwardRequestHandlerNameListFactory {
    private String forwardUrl = null;
    private String[] forwardUrlParts = null;
    private int createCount = 0;
    private final static String BEAN_NAME = "forwardRequestHandler";

    private String[] getForwardUrlParts() {
        if (forwardUrlParts == null) {
            if (StringUtils.hasText(forwardUrl)) {
                forwardUrlParts = forwardUrl.split(",");
            } else {
                forwardUrlParts = new String[] {};
            }
        }
        return forwardUrlParts;
    }

    public List<String> create() {
        ArrayList<String> rtn = new ArrayList<String>();

        int parts = getForwardUrlParts().length;

        for (int i = 0; i < parts; i++) {
            rtn.add(BEAN_NAME);
        }
        return rtn;
    }

    public String getForwardUrlPart() {
        return getForwardUrlParts()[createCount++];
    }

    public void setForwardUrl(String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

}
