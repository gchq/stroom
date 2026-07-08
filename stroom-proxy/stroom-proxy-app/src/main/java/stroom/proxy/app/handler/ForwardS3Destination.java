package stroom.proxy.app.handler;


public interface ForwardS3Destination extends ForwardDestination {

    @Override
    default DestinationType getDestinationType() {
        return DestinationType.S3;
    }
}
