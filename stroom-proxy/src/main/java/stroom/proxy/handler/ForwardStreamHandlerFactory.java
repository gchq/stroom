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
public class ForwardStreamHandlerFactory implements StreamHandlerFactory {
    private final ForwardStreamConfig forwardStreamConfig;

    @Inject
    public ForwardStreamHandlerFactory(@Nullable final ForwardStreamConfig forwardStreamConfig) {
        this.forwardStreamConfig = forwardStreamConfig;
    }

    @Override
    public List<StreamHandler> create() {
        if (forwardStreamConfig != null) {
            final String urls = forwardStreamConfig.getForwardUrl();
            if (urls != null && urls.length() > 0) {
                return Arrays
                        .stream(urls.split(","))
                        .map(url -> new ForwardStreamHandler(url, forwardStreamConfig.getForwardTimeoutMs(), forwardStreamConfig.getForwardDelayMs(), forwardStreamConfig.getForwardChunkSize()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
