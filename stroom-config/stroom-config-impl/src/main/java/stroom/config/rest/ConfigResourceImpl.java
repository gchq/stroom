package stroom.config.rest;

import stroom.config.global.api.ConfigProperty;

import javax.ws.rs.core.Response;

public class ConfigResourceImpl implements ConfigResource {

    @Override
    public Response listProperties() {
        return null;
    }

    @Override
    public Response fetch(final String name) {
        return null;
    }

    @Override
    public Response save(final String propertyName, final ConfigProperty configProperty) {
        return null;
    }
}
