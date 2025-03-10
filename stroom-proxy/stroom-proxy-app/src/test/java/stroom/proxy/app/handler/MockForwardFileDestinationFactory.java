package stroom.proxy.app.handler;

import jakarta.inject.Singleton;

@Singleton
public class MockForwardFileDestinationFactory implements ForwardFileDestinationFactory {

    private final MockForwardFileDestination forwardFileDestination;

    public MockForwardFileDestinationFactory() {
        this.forwardFileDestination = new MockForwardFileDestination();
    }

    @Override
    public ForwardDestination create(final ForwardFileConfig config) {
        return forwardFileDestination;
    }

    public MockForwardFileDestination getForwardFileDestination() {
        return forwardFileDestination;
    }
}
