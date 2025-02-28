package stroom.proxy.app.handler;

public interface ForwardHttpPostDestinationFactory {

    ForwardDestination create(ForwardHttpPostConfig forwardHttpPostConfig);
}
