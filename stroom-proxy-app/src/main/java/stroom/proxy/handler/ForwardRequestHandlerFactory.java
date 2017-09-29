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
public class ForwardRequestHandlerFactory {
    private final String forwardUrl;
    private final Integer forwardTimeoutMs;
    private final Integer forwardDelayMs;
    private final Integer forwardChunkSize;

    @Inject
    public ForwardRequestHandlerFactory(final String forwardUrl,
                                        final Integer forwardTimeoutMs,
                                        final Integer forwardDelayMs,
                                        final Integer forwardChunkSize) {
        this.forwardUrl = forwardUrl;
        this.forwardTimeoutMs = forwardTimeoutMs;
        this.forwardDelayMs = forwardDelayMs;
        this.forwardChunkSize = forwardChunkSize;
    }

    public List<RequestHandler> createHandlers() {
        if (StringUtils.hasText(forwardUrl)) {
            return Arrays
                    .stream(forwardUrl.split(","))
                    .map(url -> new ForwardRequestHandler(url, forwardTimeoutMs, forwardDelayMs, forwardChunkSize))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
