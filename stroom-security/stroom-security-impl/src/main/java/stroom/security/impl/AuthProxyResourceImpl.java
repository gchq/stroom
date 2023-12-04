package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.common.impl.ClientCredentials;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;

@AutoLogged(OperationType.UNLOGGED) // We are only proxying for the idp so no need to log it
public class AuthProxyResourceImpl implements AuthProxyResource {

    private final Provider<AuthProxyService> idpProxyServiceProvider;

    @Inject
    public AuthProxyResourceImpl(final Provider<AuthProxyService> idpProxyServiceProvider) {
        this.idpProxyServiceProvider = idpProxyServiceProvider;
    }

    @Override
    public String fetchToken(final ClientCredentials clientCredentials) {
        try {
            final String token = idpProxyServiceProvider.get().fetchToken(clientCredentials);

            return token;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        } catch (Exception e) {
            // Let the ex mapper handle it
            throw e;
        }
    }
}
