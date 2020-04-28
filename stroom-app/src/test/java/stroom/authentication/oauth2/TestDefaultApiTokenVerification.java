package stroom.authentication.oauth2;

import com.google.inject.AbstractModule;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import stroom.authentication.account.AccountService;
import stroom.authentication.api.JsonWebKeyFactory;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.token.TokenService;
import stroom.config.common.NodeUriConfig;
import stroom.security.impl.JWTService;
import stroom.security.impl.ResolvedOpenIdConfig;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.authentication.DefaultOpenIdCredentials;

import javax.inject.Inject;

/**
 * Used for generating a set of OpenId Connect credentials and a corresponding
 * API key so that stroom-proxy can connect to stroom on first boot.
 * ONLY intended for test/demo purposes.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
@ExtendWith(MockitoExtension.class)
public class TestDefaultApiTokenVerification extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDefaultApiTokenVerification.class);

    private static final String CLIENT_NAME = "Stroom Client Internal (TEST ONLY)";
    private static final String API_KEY_USER_EMAIL = "default-test-only-api-key-user";

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
//    @Rule
//    private final ResourceExtension resources = ResourceExtension.builder()
//            .addResource(this::getOAuth2Resource)
//            .build();

    @Inject
    private JsonWebKeyFactory jsonWebKeyFactory;
    @Inject
    private TokenService tokenService;
    @Inject
    private AccountService accountService;
    @Inject
    private DefaultOpenIdCredentials defaultOpenIdCredentials;
    @Inject
    private AuthenticationConfig authenticationConfig;
    @Inject
    private JWTService jwtService;
    @Inject
    private OAuth2ResourceImpl oAuth2Resource;
    @Inject
    private NodeUriConfig nodeUriConfig;

    @Mock
    private ResolvedOpenIdConfig resolvedOpenIdConfig;

    @IncludeModule(MyModule.class)

    Object getOAuth2Resource() {
        return oAuth2Resource;
    }

    @Test
    void test() throws Throwable {


        final DropwizardClientExtension dropwizard = new DropwizardClientExtension(oAuth2Resource);
        dropwizard.before();

        LOGGER.info("Base URI: " + dropwizard.baseUri().toString());

        nodeUriConfig.setHostname(dropwizard.baseUri().getHost());
        nodeUriConfig.setPort(dropwizard.baseUri().getPort());
        nodeUriConfig.setScheme("http");
        nodeUriConfig.setPathPrefix(dropwizard.baseUri().getPath());

        authenticationConfig.setUseDefaultOpenIdCredentials(true);

        String jwksUri = dropwizard.baseUri().toString() + ResolvedOpenIdConfig.INTERNAL_JWKS_URI.replace("/api", "");

        LOGGER.info("jwks uri: {}", jwksUri);

        Mockito.when(resolvedOpenIdConfig.getJwksUri()).thenReturn(jwksUri);

        String apiKey = defaultOpenIdCredentials.getApiKey();

        jwtService.verifyToken(apiKey);
    }


    class MyModule extends AbstractModule {

        @Override
        protected void configure() {
            LOGGER.info("binding");
            bind(ResolvedOpenIdConfig.class).toInstance(resolvedOpenIdConfig);
        }
    }
}
