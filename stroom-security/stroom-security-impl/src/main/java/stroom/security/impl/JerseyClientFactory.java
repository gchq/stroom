package stroom.security.impl;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;

@Singleton
public class JerseyClientFactory {
    private final Client client;

    @Inject
    JerseyClientFactory(final Environment environment, final AuthenticationConfig authenticationConfig) {
        // Create a jersey client for comms.
        client = new JerseyClientBuilder(environment).using(authenticationConfig.getJerseyClientConfiguration())
                .build(getClass().getSimpleName());
    }

    Client create() {
        return client;
    }
}
