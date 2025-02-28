package stroom.proxy.app.handler;

public interface ForwardFileDestinationFactory {

    ForwardDestination create(ForwardFileConfig config);
}
