package stroom;

import stroom.util.config.StroomProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The canonical list of external services.
 *
 * Service names, used for service discovery lookup, are obtained from configuration.
 */
public enum ExternalService {
    INDEX("index"),
    HBASE_STATS("hbaseStats"),
    SQL_STATS("sqlStats");

    private String serviceKey;

    /**
     * This maps doc ref types to services. I.e. if someone has the doc ref type they can get an ExternalService.
     */
    public static Map<String, ExternalService> docRefTypeToServiceMap = new HashMap<>();

    ExternalService(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    static {
        Stream.of(ExternalService.values()).forEach(externalService -> {
            String docRef = StroomProperties.getProperty("stroom.services." + externalService.getServiceKey() + ".docRefType");
            docRefTypeToServiceMap.put(docRef, externalService);
        });
    }

    /**
     * This is the name of the service, as obtained from configuration.
     */
    public String getServiceName() {
        return StroomProperties.getProperty("stroom.services." + serviceKey + ".name");
    }

    /**
     * This is the value in the configuration, i.e. stroom.services.<serviceKey>.name.
     */
    public String getServiceKey(){
        return serviceKey;
    }

}