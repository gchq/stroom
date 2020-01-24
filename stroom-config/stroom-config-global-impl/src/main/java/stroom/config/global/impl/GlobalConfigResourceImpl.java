package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Optional;

public class GlobalConfigResourceImpl implements GlobalConfigResource {
    private final GlobalConfigService globalConfigService;

    @Inject
    GlobalConfigResourceImpl(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public List<ConfigProperty> getAllConfig() {
        try {
            return globalConfigService.list();
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public ConfigProperty getPropertyByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
            return optConfigProperty.orElseThrow(NotFoundException::new);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public String getYamlValueByName(final String propertyName) {
        try {
            final Optional<ConfigProperty> optConfigProperty = globalConfigService.fetch(propertyName);
            return optConfigProperty
                    .flatMap(configProperty ->
                            configProperty.getYamlOverrideValue().getValue())
                    .orElseThrow(NotFoundException::new);
        } catch (final RuntimeException e) {
            throw new ServerErrorException(e.getMessage() != null
                    ? e.getMessage()
                    : e.toString(), Status.INTERNAL_SERVER_ERROR, e);
        }
    }
}
