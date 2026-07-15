/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.aws.s3.shared.S3EventResource.S3EventNotificationRequest;
import stroom.aws.s3.shared.S3Location;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.security.api.UserIdentityFactory;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.shared.ResourcePaths;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TestRemoteS3EventClient {

    @Mock
    private JerseyClientFactory jerseyClientFactory;
    @Mock
    private UserIdentityFactory userIdentityFactory;
    @Mock
    private WebTarget webTarget;
    @Mock
    private Invocation.Builder builder;
    @Mock
    private Response response;

    private DownstreamHostConfig downstreamHostConfig;
    private RemoteS3EventClient remoteS3EventClient;

    @BeforeEach
    void setUp() {
        downstreamHostConfig = DownstreamHostConfig.builder()
                .withEnabled(true)
                .withHostname("localhost")
                .withPort(8080)
                .withScheme("http")
                .build();

        remoteS3EventClient = new RemoteS3EventClient(
                jerseyClientFactory,
                userIdentityFactory,
                () -> downstreamHostConfig
        );
    }

    @Test
    void testSendNotification() {
        final S3Location s3Location = new S3Location("region", "bucket", "key");
        final S3EventNotificationRequest request = new S3EventNotificationRequest(
                s3Location, Map.of("meta", "value"));

        final String expectedUrl = "http://localhost:8080" + ResourcePaths.buildAuthenticatedApiPath(
                "/s3event/v1", "/notify");

        Mockito.when(jerseyClientFactory.createWebTarget(
                        ArgumentMatchers.eq(JerseyClientName.DOWNSTREAM),
                        ArgumentMatchers.eq(expectedUrl)))
                .thenReturn(webTarget);
        Mockito.when(webTarget.request(MediaType.APPLICATION_JSON))
                .thenReturn(builder);
        Mockito.when(builder.headers(ArgumentMatchers.any()))
                .thenReturn(builder);
        Mockito.when(builder.post(ArgumentMatchers.any(Entity.class)))
                .thenReturn(response);
        Mockito.when(response.getStatus())
                .thenReturn(200);
        Mockito.when(userIdentityFactory.getServiceUserAuthHeaders())
                .thenReturn(Collections.emptyMap());

        remoteS3EventClient.sendNotification(request);

        final ArgumentCaptor<Entity> entityCaptor = ArgumentCaptor.forClass(Entity.class);
        Mockito.verify(builder).post(entityCaptor.capture());
        Assertions.assertThat(entityCaptor.getValue().getEntity())
                .isEqualTo(request);
    }
}
