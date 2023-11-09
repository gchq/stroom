package stroom.app.guice;

import stroom.config.app.Config;
import stroom.dropwizard.common.AbstractJerseyClientFactory;
import stroom.util.NullSafe;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;

import io.dropwizard.core.setup.Environment;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class JerseyClientFactoryImpl extends AbstractJerseyClientFactory {

    // This name is used by dropwizard metrics
    private static final String JERSEY_CLIENT_NAME_PREFIX = "stroom_jersey_client_";
    private static final String JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom/";

    @SuppressWarnings("unused")
    @Inject
    public JerseyClientFactoryImpl(final Config config,
                                   final Provider<BuildInfo> buildInfoProvider,
                                   final Environment environment,
                                   final PathCreator pathCreator) {
        super(buildInfoProvider,
                environment,
                pathCreator,
                NullSafe.map(config.getJerseyClients())
        );
    }

    public String getJerseyClientNamePrefix() {
        return JERSEY_CLIENT_NAME_PREFIX;
    }

    public String getJerseyClientUserAgentPrefix() {
        return JERSEY_CLIENT_USER_AGENT_PREFIX;
    }
}
