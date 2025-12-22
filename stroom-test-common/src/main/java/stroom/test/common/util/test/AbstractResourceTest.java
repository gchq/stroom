/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test.common.util.test;

import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.RestResource;

import io.dropwizard.jersey.validation.ValidationErrorMessage;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

@ExtendWith(MockitoExtension.class)
@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class AbstractResourceTest<R extends RestResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceTest.class);

    // Need to add resources as suppliers so they can be fully mocked by mocktio before being used
    private final ResourceExtension resources = ResourceExtension.builder()
            .addResource(() -> {
                LOGGER.info("Calling getRestResource()");
                return getRestResource();
            })
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


//    public  <T_RESP> T_RESP doGetTest(final String subPath,
//                            final Class<T_RESP> responseType,
//                            final T_RESP expectedResponse) {
//
//        LOGGER.info("Calling GET on {}{}, expecting {}", getResourceBasePath(), subPath, expectedResponse);
//        T_RESP response =  resources
//            .target(getResourceBasePath())
//            .path(subPath)
//            .request()
//            .get(responseType);
//
//        Assertions.assertThat(response).isEqualTo(expectedResponse);
//
//        return response;
//    }

    public <T_RESP> T_RESP doGetTest(final String subPath,
                                     final Class<T_RESP> responseType,
                                     final T_RESP expectedResponse,
                                     final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(
                Invocation.Builder::get,
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    public <T_REQ, T_RESP> T_RESP doPostTest(final String subPath,
                                             final T_REQ request,
                                             final Class<T_RESP> responseType,
                                             final T_RESP expectedResponse,
                                             final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = getWebTarget(subPath);

        for (final Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        final Invocation.Builder builder = webTarget
                .request();

        final Entity<T_REQ> entity = Entity.json(request);

        final Response response = builder.post(entity);

        if (!isSuccessful(response.getStatus())) {
//            String json = response.readEntity(String.class);
//            LOGGER.info("json:\n{}", json);
//            System.out.println(json);

            final ValidationErrorMessage validationErrorMessage = response.readEntity(ValidationErrorMessage.class);

//            ErrorMessage errorMessage = response.readEntity(ErrorMessage.class);

            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), validationErrorMessage.getErrors()));
        }

        final T_RESP responseEntity = response.readEntity(responseType);

        if (expectedResponse != null) {
            Assertions.assertThat(responseEntity)
                    .isEqualTo(expectedResponse);
        }

        return responseEntity;
    }

    public <T_REQ, T_RESP> T_RESP doPutTest(final String subPath,
                                            final T_REQ requestEntity,
                                            final Class<T_RESP> responseType,
                                            final T_RESP expectedResponse,
                                            final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling PUT on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(builder -> builder.put(Entity.json(requestEntity)),
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    public <T_REQ> void doPutTest(final String subPath,
                                  final T_REQ requestEntity,
                                  final Function<WebTarget, WebTarget>... builderMethods) {

        LOGGER.info("Calling PUT on {}{}, passing {}",
                getResourceBasePath(), subPath, requestEntity);

        WebTarget webTarget = getWebTarget(subPath);

        for (final Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        final Invocation.Builder builder = webTarget
                .request();

        final Response response = builder.put(Entity.json(requestEntity));

        if (!isSuccessful(response.getStatus())) {
            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), response));
        }
    }

    public <T_RESP> T_RESP doDeleteTest(final String subPath,
                                        final Class<T_RESP> responseType,
                                        final T_RESP expectedResponse,
                                        final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling DELETE on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        return doTest(Invocation.Builder::delete,
                subPath,
                responseType,
                expectedResponse,
                builderMethods);
    }

    private <T_RESP> T_RESP doTest(final Function<Invocation.Builder, Response> operation,
                                   final String subPath,
                                   final Class<T_RESP> responseType,
                                   final T_RESP expectedResponse,
                                   final Function<WebTarget, WebTarget>... builderMethods) {
        LOGGER.info("Calling GET on {}{}, expecting {}",
                getResourceBasePath(), subPath, expectedResponse);

        WebTarget webTarget = getWebTarget(subPath);

        for (final Function<WebTarget, WebTarget> method : builderMethods) {
            webTarget = method.apply(webTarget);
        }

        final Invocation.Builder builder = webTarget
                .request();

        final Response response = operation.apply(builder);

        if (!isSuccessful(response.getStatus())) {
            throw new RuntimeException(LogUtil.message("Error: {} {}",
                    response.getStatus(), response));
        }

//        String json = response.readEntity(String.class);
//
//        LOGGER.info("json:\n{}", json);

        final T_RESP entity = response.readEntity(responseType);

        if (expectedResponse != null) {
            Assertions.assertThat(entity)
                    .isEqualTo(expectedResponse);
        }

        return entity;
    }

    public WebTarget getWebTarget(final String subPath) {
        return resources
                .target(getResourceBasePath())
                .path(subPath);
    }

    private boolean isSuccessful(final int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
