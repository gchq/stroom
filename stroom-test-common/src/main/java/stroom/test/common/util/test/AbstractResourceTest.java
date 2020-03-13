package stroom.test.common.util.test;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.Rule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.RestResource;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.util.function.Function;

@ExtendWith(MockitoExtension.class)
@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractResourceTest<R extends RestResource> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceTest.class);

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
    @Rule
    private final ResourceExtension resources = ResourceExtension.builder()
        .addResource(this::getRestResource)
        .build();

    private static final WebTargetFactory WEB_TARGET_FACTORY = url -> ClientBuilder.newClient(
        new ClientConfig().register(LoggingFeature.class))
        .target(url);

    public abstract R getRestResource();

    public abstract String getResourceBasePath();

    public ResourceExtension getResources() {
        return resources;
    }

    public static WebTargetFactory webTargetFactory() {
        return WEB_TARGET_FACTORY;
    }

    public  <T> T doGetTest(final String subPath,
                            final Class<T> responseType,
                            final T expectedResponse) {

        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);
        T response =  resources
            .target(getResourceBasePath())
            .path(subPath)
            .request()
            .get(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public  <T> T doGetTest(final String subPath,
                            final Class<T> responseType,
                            final T expectedResponse,
                            final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = resources
            .target(getResourceBasePath())
            .path(subPath);

        for (Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        T response =  webTarget
            .request()
            .get(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public  WebTarget getWebTarget(final String subPath) {

        return resources
            .target(getResourceBasePath())
            .path(subPath);
    }

}