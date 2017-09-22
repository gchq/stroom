package stroom.proxy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.CloseableUtil;
import stroom.util.spring.PropertyConfigurer;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class ProxyProperties {
    public final static String REMOTING_URL = "remotingUrl";
    public final static String REPO_DIR = "repoDir";
    private final static String REPOSITORY_FORMAT = "repositoryFormat";
    public final static String DB_REQUEST_VALIDATOR_JNDI_NAME = "dbRequestValidatorJndiName";
    public final static String LOG_REQUEST = "logRequest";
    public final static String FORWARD_URL = "forwardUrl";
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyProperties.class);
    private final static String FORWARD_THREAD_COUNT = "forwardThreadCount";
    public final static String BUFFER_SIZE = "bufferSize";
    private final static String FORWARD_TIMEOUT_MS = "forwardTimeoutMs";
    private final static String FORWARD_CHUNK_SIZE = "forwardChunkSize";
    private final static String DB_REQUEST_VALIDATOR_CONTEXT = "dbRequestValidatorContext";
    private final static String ROLL_CRON = "rollCron";
    private final static String READ_CRON = "readCron";
    private final static String MAX_AGGREGATION = "maxAggregation";

    private final static String REQUEST_DELAY_MS = "requestDelayMs";
    private final static String FORWARD_DELAY_MS = "forwardDelayMs";

    private final static String DB_REQUEST_VALIDATOR_AUTH_QUERY = "dbRequestValidatorAuthQuery";
    private final static String DB_REQUEST_VALIDATOR_FEED_QUERY = "dbRequestValidatorFeedQuery";

    private final static String REMOTING_READ_TIMEOUT_MS = "remotingReadTimeoutMs";
    private final static String REMOTING_CONNECT_TIMEOUT_MS = "remotingConnectTimeoutMs";

    private final static String MAX_STREAM_SIZE = "maxStreamSize";
    private final static String MAX_FILE_SCAN = "maxFileScan";

    private final static String CACHE_TIME_TO_IDLE_SECONDS = "cacheTimeToIdleSeconds";
    private final static String CACHE_TIME_TO_LIVE_SECONDS = "cacheTimeToLiveSeconds";
    private final static String DEFAULT_PROPERTIES = "/proxy.properties";
    private final static String PROPERTIES_SUFFIX = ".properties";
    private static Map<String, String> propertyDescriptionMap = new HashMap<>();
    private static String webAppProperties = null;
    private static volatile Properties properties;
    private static volatile Properties defaultProperties;

    static {
        propertyDescriptionMap.put(FORWARD_URL,
                "Optional The URL's to forward onto. This is pass-through mode if repoDir is not set");

        propertyDescriptionMap.put(FORWARD_THREAD_COUNT, "Number of threads to forward with");

        propertyDescriptionMap.put(BUFFER_SIZE, "Override default (8192) JDK buffer size to use");

        propertyDescriptionMap.put(REQUEST_DELAY_MS, "Sleep time used to aid with testing");

        propertyDescriptionMap.put(FORWARD_TIMEOUT_MS, "Time out when forwarding");

        propertyDescriptionMap.put(FORWARD_CHUNK_SIZE,
                "Chunk size to use over http(s) if not set requires buffer to be fully loaded into memory");

        propertyDescriptionMap.put(FORWARD_DELAY_MS, "Debug setting to add a delay");

        propertyDescriptionMap.put(DB_REQUEST_VALIDATOR_CONTEXT, "Database Feed Validator - Data base JDBC context");

        propertyDescriptionMap.put(DB_REQUEST_VALIDATOR_JNDI_NAME,
                "Database Feed Validator - Data base JDBC JNDI name");

        propertyDescriptionMap.put(DB_REQUEST_VALIDATOR_FEED_QUERY,
                "Database Feed Validator - SQL to check feed status");

        propertyDescriptionMap.put(DB_REQUEST_VALIDATOR_AUTH_QUERY,
                "Database Feed Validator - SQL to check authorisation required");

        propertyDescriptionMap.put(REMOTING_URL, "Url to use for remoting services");

        propertyDescriptionMap.put(REPO_DIR,
                "Optional Repository DIR. " + "If set any incoming request will be written to the file system.");

        propertyDescriptionMap.put(REPOSITORY_FORMAT,
                "Optionally supply a template for naming the files in the repository. This can be specified using multiple replacement variables.\n" +
                        "The standard template is '${pathId}/${id}' and will be used if this property is not set.\n" +
                        "This pattern will produce the following paths for the following identities:\n" +
                        "\t1 = 001.zip\n" +
                        "\t100 = 100.zip\n" +
                        "\t1000 = 001/001000.zip\n" +
                        "\t10000 = 010/010000.zip\n" +
                        "\t100000 = 100/100000.zip\n" +
                        "Other replacement variables can be used to in the template including header meta data parameters (e.g. '${feed}') and time based parameters (e.g. '${year}').\n" +
                        "Replacement variables that cannot be resolved will be output as '_'.\n" +
                        "Please ensure that all templates include the '${id}' replacement variable at the start of the file name, failure to do this will result in an invalid repository.");

        propertyDescriptionMap.put(ROLL_CRON, "Interval to roll any writing repositories.");

        propertyDescriptionMap.put(READ_CRON,
                "Cron style interval (e.g. every hour '0 * *', every half hour '0,30 * *') to read any ready " +
                        "repositories (if not defined we read all the time).");

        propertyDescriptionMap.put(MAX_AGGREGATION, "Aggregate size to break at when building an aggregate.");

        propertyDescriptionMap.put(MAX_FILE_SCAN,
                "Max number of files to scan over during forwarding.  Once this limit is it it will wait until next " +
                        "read interval");

        propertyDescriptionMap.put(MAX_STREAM_SIZE, "Stream size to break at when building an aggregate.");

        propertyDescriptionMap.put(REMOTING_CONNECT_TIMEOUT_MS, "Change from the default JVM settings.");
        propertyDescriptionMap.put(REMOTING_READ_TIMEOUT_MS, "Change from the default JVM settings.");

        propertyDescriptionMap.put(LOG_REQUEST,
                "Optional log line with header attributes output as defined by this property");

        propertyDescriptionMap.put(CACHE_TIME_TO_LIVE_SECONDS,
                "Time to live settings to used for validating feed information");

        propertyDescriptionMap.put(CACHE_TIME_TO_IDLE_SECONDS,
                "Time to idle settings to used for validating feed information");

        propertyDescriptionMap.put(LOG_REQUEST,
                "Optional log line with header attributes output as defined by this property");

        propertyDescriptionMap = Collections.unmodifiableMap(propertyDescriptionMap);
    }

    public static Properties instance() {
        if (properties == null) {
            Properties newProperties = load();
            updateJavaSystemProperties(newProperties);
            trace(newProperties);
            properties = newProperties;
        }
        return properties;
    }

    /**
     * Used for testing
     */
    public static void setOverrideProperties(Properties overrideProperties) {
        properties = overrideProperties;
        PropertyConfigurer.setOverrideProperties(overrideProperties);
    }

    public static Properties getDefaultProperties() {
        return defaultProperties;
    }

    public static void setDefaultProperties(Properties defaultProperties) {
        ProxyProperties.defaultProperties = defaultProperties;
    }

    private static Properties load() {
        Properties properties = new Properties();

        URL url = null;

        if (webAppProperties != null) {
            url = ProxyProperties.class.getResource(webAppProperties);
        }
        if (url == null) {
            url = ProxyProperties.class.getResource(DEFAULT_PROPERTIES);
        }

        if (url == null) {
            LOGGER.error("Failed to find properties %s or %s", webAppProperties, DEFAULT_PROPERTIES);

        } else {
            InputStream is = null;
            try {
                is = url.openStream();

                if (is == null) {
                    LOGGER.error("Failed to load properties from %s", url);
                } else {
                    LOGGER.debug("Loaded properties from %s", url);
                    properties.load(is);
                }
            } catch (Exception ex) {
                LOGGER.error("Exception when loading properties from %s %s", url, ex.getMessage());
            } finally {
                CloseableUtil.closeLogAndIngoreException(is);
            }
        }
        return properties;
    }

    /**
     * Any properties that start with java set them as system properties. This
     * allows the key store stuff to no longer be passed on the command line.
     */
    private static void updateJavaSystemProperties(Properties properties) {
        properties.keySet().stream()
                .map(String::valueOf)
                .filter(key -> key.equals("java"))
                .forEach(key -> {
                    String value = properties.getProperty(key);
                    LOGGER.info("updateJavaSystemProperties() - Using property " + key + "=" + value);
                    System.setProperty(key, value);
                });

        //TODO old java7 code
//        for (Entry<Object, Object> map : properties.entrySet()) {
//            String key = String.valueOf(map.getKey());
//            String value = String.valueOf(map.getValue());
//            if (key.startsWith("java")) {
//                LOGGER.info("updateJavaSystemProperties() - Using property " + key + "=" + value);
//                System.setProperty(key, value);
//            }
//        }

    }

    /**
     * @return reload the class path properties and return true if they are
     * different.
     */
    public static boolean rescan() {
        Properties currentProperties = instance();
        Properties newProperties = load();

        if (newProperties.isEmpty()) {
            LOGGER.warn("rescan() - Failed to load properties or they are empty");
            // Nothing changed
            return false;
        }

        if (!validate(newProperties)) {
            LOGGER.warn("rescan() - New properties are not valid .... ignore them");
            // Nothing changed
            return false;
        }

        if (currentProperties.equals(newProperties)) {
            LOGGER.debug("rescan() - nothing changed");
            return false;
        } else {
            LOGGER.debug("rescan() - property changes detected!!");

            properties = newProperties;
            trace(newProperties);

            // No point updating system properties
            updateJavaSystemProperties(newProperties);

            return true;
        }
    }

    private static void trace(Properties properties) {
        for (Entry<Object, Object> map : properties.entrySet()) {
            LOGGER.info("trace() - Using property " + map.getKey() + "=" + map.getValue());
        }
    }

    public static boolean validate(Properties properties) {
        if (!(isDefined(properties, FORWARD_URL) || isDefined(properties, REPO_DIR))) {
            LOGGER.error("Must define %s or %s", FORWARD_URL, REPO_DIR);
            return false;
        }
        if (isDefined(properties, REMOTING_URL) && isDefined(properties, DB_REQUEST_VALIDATOR_JNDI_NAME)) {
            LOGGER.error("Must not define both %s and %s", REMOTING_URL, DB_REQUEST_VALIDATOR_JNDI_NAME);
            return false;
        }
        return true;
    }

    public static boolean validate() {
        return validate(instance());
    }

    public static boolean isDefined(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value != null && value.length() > 0;
    }

    public static boolean isDefined(String key) {
        return isDefined(instance(), key);
    }

    public static void setWebAppPropertiesName(final String name) {
        if (name == null) {
            webAppProperties = null;
        } else {
            webAppProperties = "/" + name + PROPERTIES_SUFFIX;
        }
    }

    public static Map<String, String> getPropertyDescriptionMap() {
        return propertyDescriptionMap;
    }

    public static void main(String[] args) {
        ArrayList<String> keys = new ArrayList<>(propertyDescriptionMap.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            System.out.println(key + "=" + propertyDescriptionMap.get(key));
        }
    }

}
