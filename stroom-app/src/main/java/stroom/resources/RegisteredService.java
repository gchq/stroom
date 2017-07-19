package stroom.resources;

import com.google.common.base.Preconditions;
import stroom.servicediscovery.ExternalService;

/**
 * Defines the versioned services that stroom will present externally
 */
public enum RegisteredService {
    INDEX_V1(ExternalService.INDEX, ResourcePaths.STROOM_INDEX, 1),
    SQL_STATISTICS_V1(ExternalService.SQL_STATISTICS, ResourcePaths.SQL_STATISTICS, 1),
    AUTHENTICATION_V1(ExternalService.AUTHENTICATION, ResourcePaths.AUTHENTICATION, 1),
    AUTHORISATION_V1(ExternalService.AUTHORISATION, ResourcePaths.AUTHORISATION, 1);

    private final ExternalService externalService;
    private final String subPath;
    private final int version;

    RegisteredService(final ExternalService externalService, final String subPath, final int version) {
        Preconditions.checkArgument(externalService.getType().equals(ExternalService.Type.SERVER) ||
        externalService.getType().equals(ExternalService.Type.CLIENT_AND_SERVER),
                "Incorrect type for defining as a registered service");
        this.externalService = externalService;
        this.subPath = subPath;
        this.version = version;
    }

    public String getVersionedPath() {
        return subPath + "/v" + version;
    }

    public String getSubPath() {
        return subPath;
    }

    public ExternalService getExternalService() {
        return externalService;
    }

    public String getVersionedServiceName() {
        return externalService.getVersionedServiceName();
    }
}
