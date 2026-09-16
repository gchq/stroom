/*
 * Copyright 2026 Crown Copyright
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

import stroom.aws.s3.client.PooledClient;
import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3EventResource.S3EventNotificationRequest;
import stroom.cache.api.TemplateCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ForwardS3Config.NotificationType;
import stroom.util.string.TemplateUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestS3Destination {

    private S3Client s3Client;
    private RemoteS3EventClient eventClient;
    private Path sourcesDir;

    @BeforeEach
    void setUp(@TempDir final Path baseDir) {
        s3Client = Mockito.mock(S3Client.class);
        eventClient = Mockito.mock(RemoteS3EventClient.class);
        sourcesDir = baseDir.resolve("sources");
    }

    private S3Destination destination(final NotificationType notificationType) {
        final S3ClientConfig clientConfig = S3ClientConfig.builder()
                .region("eu-west-2")
                .bucketName("proxy-${feed}")
                .keyPattern("${feed}/${uuid}.zip")
                .build();
        final ForwardS3Config config = new ForwardS3Config(
                true, false, notificationType, "s3-dest", clientConfig, null, null, null, null);
        final PooledClient<S3Client> pooled = new PooledClient<>() {
            @Override
            public S3Client getClient() {
                return s3Client;
            }

            @Override
            public void close() {
            }
        };
        // Implemented rather than mocked: the helper uses the pool's default methods.
        final S3ClientPool pool = new S3ClientPool() {
            @Override
            public PooledClient<S3Client> getPooledS3Client(final S3ClientConfig config) {
                return pooled;
            }

            @Override
            public PooledClient<S3AsyncClient> getPooledS3AsyncClient(final S3ClientConfig config) {
                throw new UnsupportedOperationException("sync only");
            }
        };
        final TemplateCache templateCache = Mockito.mock(TemplateCache.class);
        Mockito.when(templateCache.getTemplate(Mockito.any()))
                .thenAnswer(invocation -> TemplateUtil.parseTemplate(invocation.getArgument(0, String.class)));
        return new S3Destination(
                "s3-dest", config, pool, templateCache, Mockito.mock(S3MetaKeysMapper.class), eventClient);
    }

    @Test
    void testTheZipIsUploadedUnderTheTemplatedKeyAndTheDownstreamNotified() throws Exception {
        final Path group = createGroup(Map.of("Feed", "test_feed", "Type", "Raw Events"));
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(Path.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        destination(NotificationType.REST).deliver(group);

        final ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        Mockito.verify(s3Client).putObject(requests.capture(), Mockito.eq(new FileGroup(group).getZip()));
        assertThat(requests.getValue().bucket()).isEqualTo("proxy-test_feed");
        assertThat(requests.getValue().key()).startsWith("test_feed/").endsWith(".zip");
        final ArgumentCaptor<S3EventNotificationRequest> notifications =
                ArgumentCaptor.forClass(S3EventNotificationRequest.class);
        Mockito.verify(eventClient).sendNotification(notifications.capture());
        assertThat(notifications.getValue().getEntityTag()).isEqualTo("etag-1");
        assertThat(group).as("the destination deletes nothing").isDirectory();
    }

    @Test
    void testNoNotificationWhenS3EventsTellTheDownstream() throws Exception {
        final Path group = createGroup(Map.of("Feed", "test_feed"));
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(Path.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        destination(NotificationType.S3_EVENT).deliver(group);

        Mockito.verifyNoInteractions(eventClient);
    }

    @Test
    void testAnUploadThatThrowsIsATransientFailure() throws Exception {
        final Path group = createGroup(Map.of("Feed", "test_feed"));
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(Path.class)))
                .thenThrow(S3Exception.builder().message("slow down").statusCode(503).build());

        assertThatThrownBy(() -> destination(NotificationType.S3_EVENT).deliver(group))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(Refused.class)
                .hasMessageContaining("slow down");
        Mockito.verifyNoInteractions(eventClient);
    }

    @Test
    void testANotificationThatThrowsIsATransientFailureAfterTheUpload() throws Exception {
        final Path group = createGroup(Map.of("Feed", "test_feed"));
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(Path.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());
        Mockito.doThrow(new RuntimeException("downstream away")).when(eventClient).sendNotification(Mockito.any());

        assertThatThrownBy(() -> destination(NotificationType.REST).deliver(group))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("downstream away");
    }

    @Test
    void testAGivenUpGroupsErrorLogIsUploadedBesideTheZip() throws Exception {
        final Path group = createGroup(Map.of("Feed", "test_feed"));
        Files.writeString(group.resolve(FileGroup.ERROR_LOG_FILE_NAME), "failure: refused\n");
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(Path.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build());

        destination(NotificationType.S3_EVENT).deliver(group);

        final ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        Mockito.verify(s3Client, Mockito.times(2)).putObject(requests.capture(), Mockito.any(Path.class));
        final List<PutObjectRequest> all = requests.getAllValues();
        assertThat(all.get(1).key()).isEqualTo(all.get(0).key() + "." + FileGroup.ERROR_LOG_FILE_NAME);
        assertThat(all.get(1).contentType()).isEqualTo("text/plain");
    }

    private Path createGroup(final Map<String, String> attrs) throws IOException {
        final Path dir = sourcesDir.resolve("source");
        Files.createDirectories(dir);
        final FileGroup fileGroup = new FileGroup(dir);
        Files.writeString(fileGroup.getZip(), "zip");
        Files.writeString(fileGroup.getEntries(), "");
        AttributeMapUtil.write(new AttributeMap(attrs), fileGroup.getMeta());
        return dir;
    }
}
