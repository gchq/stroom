package stroom.connectors;

/**
 * An interface for creating {@link P} instances.
 * A given factory is expected to be able to create instances for a specific version of the connector.
 * We are leaving the door open to multiple versions of connectors integrating into a given instance (maybe...)
 *
 * @param <P> The producer class.
 */
public interface StroomConnectorFactory<P extends StroomConnector> {

    /**
     * Given a specific version and the connector properties, creates/returns the specific producer
     * found in the external library. Will only succeed if the factory is of the correct version.
     *
     * A {@link RuntimeException} will be thrown in the event of problems creating and initialising
     * the connector
     *
     * @param version The named version for the external library to adhere to
     * @param properties The connector properties shim
     * @return Either a connected {@link P} or null if the version was a mismatch.
     */
    P createConnector(final String version, ConnectorProperties properties);
}
