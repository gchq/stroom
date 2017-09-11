package stroom.connectors;

public interface StroomConnector {
    /**
     * Should be called when the producer is no longer required.
     */
    void shutdown();
}
