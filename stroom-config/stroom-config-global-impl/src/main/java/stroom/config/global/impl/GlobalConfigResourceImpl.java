package stroom.config.global.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.global.shared.ConfigProperty;
import stroom.security.api.SecurityContext;
import stroom.util.RestResource;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

public class GlobalConfigResourceImpl implements GlobalConfigResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigResourceImpl.class);

    private final SecurityContext securityContext;
    private final GlobalConfigService globalConfigService;

    @Inject
    GlobalConfigResourceImpl(final SecurityContext securityContext,
                             final GlobalConfigService globalConfigService) {
        this.securityContext = securityContext;
        this.globalConfigService = globalConfigService;
    }

    @Override
    public Response getAllConfig() {
        try {
            final List<ConfigProperty> configProperties = globalConfigService.list();
            Response response = Response.ok(configProperties).build();
            return response;
        } catch (Exception e) {
            return Response.serverError()
                    .entity(RestResource.buildErrorResponse(e))
                    .build();
        }
    }

    @Override
    public Response getPropertyByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);

            return optConfigProperty
                    .map(configProperty ->
                            Response.ok().entity(configProperty).build())
                    .orElseGet(() ->
                            Response.status(Response.Status.NOT_FOUND).build());
        } catch (Exception e) {
            return Response.serverError()
                    .entity(RestResource.buildErrorResponse(e))
                    .build();
        }
    }

    @Override
    public Response getYamlValueByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);

            Response response = optConfigProperty
                    .flatMap(configProperty ->
                            configProperty.getYamlOverrideValue().getValue())
                    .map(configProperty ->
                            Response.ok().entity(configProperty).build())
                    .orElseGet(() ->
                            Response.status(Response.Status.NOT_FOUND).build());
            return response;
        } catch (Exception e) {
            return Response.serverError()
                    .entity(RestResource.buildErrorResponse(e))
                    .build();
        }
    }
}
