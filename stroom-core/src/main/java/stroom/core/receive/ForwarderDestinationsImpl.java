package stroom.core.receive;

import stroom.proxy.repo.ForwarderDestinations;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StreamTargetStreamHandlers;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

public class ForwarderDestinationsImpl implements ForwarderDestinations {
    private final StreamTargetStreamHandlers streamTargetStreamHandlers;

    @Inject
    public ForwarderDestinationsImpl(final StreamTargetStreamHandlers streamTargetStreamHandlers) {
        this.streamTargetStreamHandlers = streamTargetStreamHandlers;
    }

    @Override
    public List<String> getDestinationNames() {
        return Collections.singletonList("stroom");
    }

    @Override
    public StreamHandlers getProvider(final String forwardUrl) {
        return streamTargetStreamHandlers;
    }
}
