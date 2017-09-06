package stroom.connectors;

public interface StroomConnectorProducer {
    /**
     * Should be called when the producer is no longer required.
     */
    void shutdown();
}
