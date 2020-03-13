package stroom.test.common.util.test;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import stroom.util.shared.RestResource;

import javax.ws.rs.client.WebTarget;
import java.util.function.Function;

@ExtendWith(MockitoExtension.class)
@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractResourceTest<R extends RestResource> {

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
    @Rule
    private final ResourceExtension resources = ResourceExtension.builder()
        .addResource(this::getRestResource)
        .build();

    public abstract R getRestResource();

    public abstract String getResourceBasePath();

    public ResourceExtension getResources() {
        return resources;
    }

    public  <T> T doGet(final String subPath,
                          final Class<T> responseType,
                          final T expectedResponse) {

        T response =  resources
            .target(getResourceBasePath())
            .path(subPath)
            .request()
            .get(responseType);

        Assertions.assertThat(response).isEqualTo(expectedResponse);

        return response;
    }

    public  <T> T doGet(final String subPath,
                          final Class<T> responseType,
                          final T expectedResponse,
                          final Function<WebTarget, WebTarget>... builderMethods) {

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