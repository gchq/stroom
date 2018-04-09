package stroom.connectors;

import stroom.node.server.StroomPropertyService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The generic form of a Connector Factory Service.
 *
 * A specific sub class of this would be
 * 1) Loaded as a Service
 * 2) Each Factory would be capable of creating Connectors for specific versions of the external library
 *
 * @param <C> The Connector class
 * @param <F> The Factory class, which is tied to creating instances of the Connector.
 */
public abstract class StroomAbstractConnectorFactoryService<
        C extends StroomConnector,
        F extends StroomConnectorFactory<C>> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(StroomAbstractConnectorFactoryService.class);

    // If a class requests a producer, but gives no name, use this value as the name
    private static final String DEFAULT_NAME = "default";
    private static final int TIME_BETWEEN_INIT_ATTEMPS_MS = 10_000;

    // This property should exist with the given property prefix.
    private static final String PROP_CONNECTOR_VERSION = "connector.version";

    private final String propertyPrefix;
    private final StroomPropertyService propertyService;

    private final ExternalLibService externalLibService;
    private final Class<C> connectorClass;
    private final Class<F> factoryClass;

    private volatile Instant timeOfLastFailedInitAttempt = Instant.EPOCH;

    // Used to keep track of the instances created, a single instance per name will be created and shared.
    //TODO If we want to have multiple versions of a connector, e.g. kafka 0.10.1 and 0.10.2 then we will
    //need to key on name+version
    private final ConcurrentMap<String, C> connectorsByName;

    protected StroomAbstractConnectorFactoryService(final StroomPropertyService propertyService,
                                                    final ExternalLibService externalLibService,
                                                    final String propertyPrefix,
                                                    final Class<C> connectorClass,
                                                    final Class<F> factoryClass) {
        this.propertyService = propertyService;
        this.propertyPrefix = propertyPrefix;
        this.externalLibService = externalLibService;
        this.connectorClass = connectorClass;
        this.factoryClass = factoryClass;
        this.connectorsByName = new ConcurrentHashMap<>();
    }

    /**
     * Overloaded version of getConnector that uses the default name.
     *
     * A RuntimeException will be thrown in the event that the connector implementation cannot be initialised
     * using the configuration defined in the properties
     *
     * @return An initialised and configured {@link C} if an implementation is available for the connector version
     * defined in properties.
     */
    public Optional<C> getConnector() {
        return getConnector(DEFAULT_NAME);
    }

    /**
     * Users of this service can request a named Kafka connection. This is used to merge in the property names of the
     * bootstrap servers and the kafka client version.
     *
     * A RuntimeException will be thrown in the event that the connector implementation cannot be initialised
     * using the configuration defined in the properties
     *
     * @param name The named configuration of producer.
     * @return An initialised and configured {@link C} if an implementation is available for the connector version
     * defined in properties.
     */
    public Optional<C> getConnector(final String name) {

        //First try and find a previously created producer by this name
        //Doing a get() then later a computeIfAbsent to avoid the locking that computeIfAbsent does
        //on a read when the entry is present. connectorsByName will be read heavy
        C connector = connectorsByName.get(name);

        if (connector != null) {
            //we already have a connector for this name so use that
            return Optional.of(connector);
        } else {
            connector = connectorsByName.computeIfAbsent(
                    name,
                    //get connector may throw an exception on initialisation
                    k -> initConnector(name));

            //connector could be null if no implementation class can be found with the external class loader
            return Optional.ofNullable(connector);
        }
    }

    private C initConnector(final String name) {

        //only try to init every TIME_BETWEEN_INIT_ATTEMPS_MS
        if (isOkToInitNow()) {
            timeOfLastFailedInitAttempt = Instant.now();

            // Create a properties shim for the named producer.
            final ConnectorProperties connectorProperties = new ConnectorPropertiesPrefixImpl(
                    String.format(propertyPrefix, name),
                    this.propertyService);

            // Retrieve the settings for this named kafka
            String connectorVersion = connectorProperties.getProperty(PROP_CONNECTOR_VERSION);

            LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Attempting to initialise connector with version [{}] using factory [{}]",
                    connectorVersion,
                    factoryClass.getName()));

            if (connectorVersion == null) {
                //no point going to the factory as we have no version to look up
                LAMBDA_LOGGER.warn(() -> LambdaLogger.buildMessage(
                        "ConnectorVersion is null for factoryClass [{}] and name [{}]",
                        factoryClass.getName(),
                        name));
                return null;
            }

            C connector = null;
            try {
                // Loop through all the class loaders created for external JARs
                for (final ClassLoader classLoader : externalLibService.getClassLoaders()) {
                    // Create a service loader for our specific factory class
                    final ServiceLoader<F> serviceLoader = ServiceLoader.load(factoryClass, classLoader);
                    // Iterate through the factories to see if any can create a connector for the right version.
                    for (final F factory : serviceLoader) {
                        //the creation of the connector may throw an exception
                        connector = factory.createConnector(connectorVersion, connectorProperties);
                        if (connector != null) {
                            break;
                        }
                    }
                }

                if (connector == null) {
                    LAMBDA_LOGGER.warn(() ->
                            LambdaLogger.buildMessage("Unable to find connector with version [{}] using factory [{}], is the implementation " +
                                            "jar correctly installed",
                                    connectorVersion,
                                    factoryClass.getName()));
                }
            } catch (Exception e) {
                String factoryClassName = factoryClass.getName();
                LAMBDA_LOGGER.error(() ->
                        LambdaLogger.buildMessage("Unable to initialise connector [{}] [{}] due to [{}], (enable DEBUG for full stack)",
                                factoryClassName,
                                connectorVersion,
                                e.getMessage()));
                LAMBDA_LOGGER.debug(() ->
                        LambdaLogger.buildMessage("Unable to initialise connector [{}] [{}]",
                                factoryClassName,
                                connectorVersion,
                                e));
                //don't throw exception, callers can just handling having a null connector
            }
            return connector;
        } else {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Won't try to init [{}] now, time since last failed attempt is [{}]",
                    factoryClass.getName(),
                    Duration.between(timeOfLastFailedInitAttempt, Instant.now()).toString()));
            return null;
        }
    }

    public void shutdown() {
        LAMBDA_LOGGER.info(() -> "Shutting Down Stroom Connector Producer Factory Service");
        connectorsByName.values().forEach(StroomConnector::shutdown);
        connectorsByName.clear();
    }

    protected StroomPropertyService getPropertyService() {
        return propertyService;
    }

    /**
     * A check to prevent many process trying to repeatedly init a connector. This means init will only be attempted
     * every TIME_BETWEEN_INIT_ATTEMPS_MS to prevent the logs being filled up if the connector client config is incorrect
     * of the connector's destination is unavailable.
     */
    private boolean isOkToInitNow() {
        return Instant.now().isAfter(timeOfLastFailedInitAttempt.plusMillis(TIME_BETWEEN_INIT_ATTEMPS_MS));
    }

}
