package stroom.proxy.handler;

import org.springframework.util.StringUtils;

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
    public ForwardRequestHandlerFactory(final ForwardRequestConfig forwardRequestConfig) {
        this.forwardRequestConfig = forwardRequestConfig;
    }

    @Override
    public List<RequestHandler> create() {
        if (StringUtils.hasText(forwardRequestConfig.getForwardUrl())) {
            return Arrays
                    .stream(forwardRequestConfig.getForwardUrl().split(","))
                    .map(url -> new ForwardRequestHandler(url, forwardRequestConfig.getForwardTimeoutMs(), forwardRequestConfig.getForwardDelayMs(), forwardRequestConfig.getForwardChunkSize()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
