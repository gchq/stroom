package stroom.rs.logging.impl;

import stroom.util.guice.RestResourcesBinder.ResourceType;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class ResourcePathMapProvider implements Provider<ResourcePathMap> {

    private final Map<ResourceType, Provider<RestResource>> providerMap;
    private ResourcePathMap resourcePathMap;

    @Inject
    ResourcePathMapProvider (final Map<ResourceType, Provider<RestResource>> providerMap){
        this.providerMap = providerMap;
    }

    @Override
    public ResourcePathMap get() {
        if (resourcePathMap == null){
            resourcePathMap = new ResourcePathMap(providerMap);
        }
        return resourcePathMap;
    }
}
