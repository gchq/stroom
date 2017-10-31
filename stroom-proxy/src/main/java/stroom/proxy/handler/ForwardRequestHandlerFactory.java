package stroom.proxy.handler;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler class that forwards the request to a URL.
 */
public class ForwardRequestHandlerFactory implements HandlerFactory {
    private final ForwardRequestConfig forwardRequestConfig;

    @Inject
    public ForwardRequestHandlerFactory(@Nullable final ForwardRequestConfig forwardRequestConfig) {
        this.forwardRequestConfig = forwardRequestConfig;
    }

    @Override
    public List<RequestHandler> create() {
        if (forwardRequestConfig != null) {
            final String urls = forwardRequestConfig.getForwardUrl();
            if (urls != null && urls.length() > 0) {
                return Arrays
                        .stream(urls.split(","))
                        .map(url -> new ForwardRequestHandler(url, forwardRequestConfig.getForwardTimeoutMs(), forwardRequestConfig.getForwardDelayMs(), forwardRequestConfig.getForwardChunkSize()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
