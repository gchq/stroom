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

package stroom.planb.impl.data;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.shared.PlanBDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.shared.ThreadPool;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.io.StreamUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.time.StroomDuration;
import stroom.util.zip.ZipUtil;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileTransferService extends AbstractResourceTest<FileTransferResource> {

    private static final String THIS_NODE = "thisNode";
    private static final String REMOTE_NODE = "remoteNode";
    private static final int SEND_PART_ATTEMPTS = 3;

    private static ExecutorService executorService;
    private static ExecutorProvider executorProvider;

    @Mock
    private FileTransferService fileTransferService;

    @BeforeAll
    static void beforeAll() {
        executorService = Executors.newCachedThreadPool();
        executorProvider = new ExecutorProvider() {

            @Override
            public Executor get() {
                return executorService;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return executorService;
            }
        };
    }

    @AfterAll
    static void afterAll() {
        executorService.shutdown();
    }

    @Test
    void testStorePartRemotely() throws IOException {
        final Path path = Files.createTempFile("test", "test");
        Files.writeString(path, "TestFileTransferService");
        final String inputFileHash = FileHashUtil.hash(path);
        final boolean merge = true;
        final long inputCreateTime = System.currentTimeMillis();
        final FileDescriptor fileDescriptor = new FileDescriptor(inputCreateTime, 1, inputFileHash);
        final FileTransferClientImpl fileTransferClient = new FileTransferClientImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                executorProvider);

        Mockito
                .doAnswer(invocation -> {
                    final long createTime = invocation.getArgument(0);
                    final long metaId = invocation.getArgument(1);
                    final String fileHash = invocation.getArgument(2);
                    final String fileName = invocation.getArgument(3);
                    final boolean synchroniseMerge = invocation.getArgument(4);
                    final InputStream inputStream = invocation.getArgument(5);
                    assertThat(createTime).isEqualTo(inputCreateTime);
                    assertThat(metaId).isEqualTo(1);
                    assertThat(fileHash).isEqualTo(inputFileHash);
                    assertThat(synchroniseMerge).isEqualTo(merge);
                    assertThat(StreamUtil.streamToString(inputStream)).isEqualTo("TestFileTransferService");
                    return null;
                })
                .when(fileTransferService).receivePart(
                        Mockito.anyLong(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyBoolean(),
                        Mockito.any(InputStream.class));

        final WebTarget webTarget = getWebTarget(FileTransferResource.SEND_PART_PATH_PART);
        fileTransferClient.storePartRemotely(webTarget, fileDescriptor, path, merge);
    }

    @Test
    void testFetchSnapshot() throws IOException {
        final Instant lastWriteTime = Instant.now();
        final Path sourceDir = Files.createTempDirectory("test");
        final Path testFile = sourceDir.resolve("test.txt");
        Files.writeString(testFile, "TestFileTransferService");
        final Path infoFile = sourceDir.resolve(Shard.SNAPSHOT_INFO_FILE_NAME);
        Files.writeString(infoFile, lastWriteTime.toString());
        final Path zipFile = sourceDir.resolve("test.zip");
        ZipUtil.zip(zipFile, sourceDir);
        final Path targetDir = Files.createTempDirectory("test");

        final DocRef planBDocRef = DocRef.builder().type(PlanBDoc.TYPE).uuid("TestUuid").name("TestMap").build();
        final long requestTime = System.currentTimeMillis();
        final FileTransferClientImpl fileTransferClient = new FileTransferClientImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                executorProvider);

        Mockito
                .doAnswer(invocation -> {
                    final SnapshotRequest request = invocation.getArgument(0);

                    // If we already have a snapshot for the current write time then don't supply one and just
                    // return an error.
                    if (request.getCurrentSnapshotTime() != null &&
                        Objects.equals(lastWriteTime.toEpochMilli(), request.getCurrentSnapshotTime())) {
                        throw new NotModifiedException();
                    }

                    assertThat(request.getPlanBDocRef()).isEqualTo(planBDocRef);
                    assertThat(request.getEffectiveTime()).isEqualTo(requestTime);
                    return Files.newInputStream(zipFile);
                })
                .when(fileTransferService).openSnapshot(
                        Mockito.any(SnapshotRequest.class));

        final SnapshotRequest request = new SnapshotRequest(planBDocRef, requestTime, null);
        final WebTarget webTarget = getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
        final Instant snapshotTime = fileTransferClient.fetchSnapshot(webTarget, request, targetDir);
        assertThat(snapshotTime).isBeforeOrEqualTo(Instant.now());

        final Path targetFile = targetDir.resolve("test.txt");
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.readString(targetFile)).isEqualTo("TestFileTransferService");

        // Test error if snapshot is up to date.
        assertThatThrownBy(() -> {
            final SnapshotRequest req = new SnapshotRequest(planBDocRef, requestTime, lastWriteTime.toEpochMilli());
            final WebTarget wt = getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
            fileTransferClient.fetchSnapshot(wt, req, targetDir);
        }).isInstanceOf(NotModifiedException.class);
    }

    @Override
    public FileTransferResource getRestResource() {
        return new FileTransferResourceImpl(() -> fileTransferService);
    }

    @Override
    public String getResourceBasePath() {
        return FileTransferResource.BASE_PATH;
    }

    /**
     * A missing snapshot must be reported as an error status, not as a successful response with an empty body.
     * The status is committed before streaming starts, so anything that can fail has to fail before that. The
     * client would otherwise unzip nothing and fail later with a confusing error about a missing info file.
     * See gh-5689.
     */
    @Test
    void testMissingSnapshotIsNotReportedAsSuccess(@TempDir final Path tempDir) {
        Mockito
                .doThrow(new SnapshotNotFoundException("No snapshot has been created yet for MyMAP"))
                .when(fileTransferService).openSnapshot(Mockito.any(SnapshotRequest.class));

        final DocRef planBDocRef = DocRef.builder().type(PlanBDoc.TYPE).uuid("test-uuid").name("MyMAP").build();
        final SnapshotRequest request = new SnapshotRequest(planBDocRef, 0L, null);
        final WebTarget webTarget = getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART);

        assertThatThrownBy(() -> fileTransferClient().fetchSnapshot(webTarget, request, tempDir))
                .isInstanceOf(SnapshotNotFoundException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("No snapshot has been created yet");
    }

    /**
     * A NOT_MODIFIED answer is the store node confirming that the snapshot we already hold is current, not a
     * failure to fetch one. It must reach the caller as a {@link NotModifiedException}, as wrapping it hides the
     * type, making an unchanged store look like a failing one until the snapshot ages out and reads start
     * failing. See gh-5705.
     */
    @Test
    void testNotModifiedIsNotWrapped(@TempDir final Path tempDir) {
        Mockito
                .doThrow(new NotModifiedException())
                .when(fileTransferService).openSnapshot(Mockito.any(SnapshotRequest.class));

        final DocRef planBDocRef = DocRef.builder().type(PlanBDoc.TYPE).uuid("test-uuid").name("MyMAP").build();
        final SnapshotRequest request = new SnapshotRequest(planBDocRef, 0L, System.currentTimeMillis());

        assertThatThrownBy(() -> nodeCallingFileTransferClient().fetchSnapshot(REMOTE_NODE, request, tempDir))
                .isInstanceOf(NotModifiedException.class)
                .hasMessageContaining("304");
    }

    /**
     * A genuine failure must not be reported as a 404, which would be indistinguishable from there simply being
     * no snapshot yet.
     */
    @Test
    void testUnexpectedFailureIsReportedAsServerError(@TempDir final Path tempDir) {
        Mockito
                .doThrow(new RuntimeException("Disk exploded"))
                .when(fileTransferService).openSnapshot(Mockito.any(SnapshotRequest.class));

        final DocRef planBDocRef = DocRef.builder().type(PlanBDoc.TYPE).uuid("test-uuid").name("MyMAP").build();
        final SnapshotRequest request = new SnapshotRequest(planBDocRef, 0L, null);
        final WebTarget webTarget = getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART);

        assertThatThrownBy(() -> fileTransferClient().fetchSnapshot(webTarget, request, tempDir))
                .isNotInstanceOf(SnapshotNotFoundException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("Disk exploded");
    }

    /**
     * A failure to send a part to a node must reach the caller, as that is what fails the processing task and so
     * gets the reason into the stream processing error file. This client only logs such a failure at debug, so
     * the rethrow is the only thing reporting it. See gh-5706.
     * <p>
     * The node answering, even with an error, must not be retried. It may have received and queued the part
     * already, and the additive stores do not merge the same part twice without double counting.
     */
    @Test
    void testFailureToSendPartReachesCallerAndIsNotRetried() throws Exception {
        final Path path = Files.createTempFile("test", "test");
        Files.writeString(path, "TestFileTransferService");
        final FileDescriptor fileDescriptor = new FileDescriptor(
                System.currentTimeMillis(),
                1,
                FileHashUtil.hash(path));

        Mockito
                .doThrow(new RuntimeException("Disk exploded"))
                .when(fileTransferService).receivePart(
                        Mockito.anyLong(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.anyBoolean(),
                        Mockito.any(InputStream.class));

        final FileTransferClientImpl fileTransferClient = partSendingFileTransferClient(
                url -> getWebTarget(FileTransferResource.SEND_PART_PATH_PART));

        assertThatThrownBy(() -> fileTransferClient.storePart(fileDescriptor, path, true))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Unable to send file to '" + REMOTE_NODE + "'")
                .hasMessageContaining("after 1 attempt(s)")
                .hasMessageContaining("Disk exploded");

        Mockito.verify(fileTransferService, Mockito.times(1)).receivePart(
                Mockito.anyLong(),
                Mockito.anyLong(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyBoolean(),
                Mockito.any(InputStream.class));
    }

    /**
     * A failure to connect to a node, e.g. the DNS lookup failing during a network blip, is retried, as nothing
     * can have been delivered. The part is only reported as undeliverable once the attempts are used up.
     * See gh-5706.
     */
    @Test
    void testConnectionFailureIsRetried() throws Exception {
        final Path path = Files.createTempFile("test", "test");
        Files.writeString(path, "TestFileTransferService");
        final FileDescriptor fileDescriptor = new FileDescriptor(
                System.currentTimeMillis(),
                1,
                FileHashUtil.hash(path));

        final Invocation.Builder builder = Mockito.mock(Invocation.Builder.class);
        Mockito.when(builder.header(Mockito.anyString(), Mockito.any())).thenReturn(builder);
        Mockito.when(builder.post(Mockito.any(Entity.class)))
                .thenThrow(new ProcessingException(new UnknownHostException("no.such.host")));
        final WebTarget unreachableTarget = Mockito.mock(WebTarget.class);
        Mockito.when(unreachableTarget.request()).thenReturn(builder);

        final FileTransferClientImpl fileTransferClient =
                partSendingFileTransferClient(url -> unreachableTarget);

        assertThatThrownBy(() -> fileTransferClient.storePart(fileDescriptor, path, true))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Unable to send file to '" + REMOTE_NODE + "'")
                .hasMessageContaining("after " + SEND_PART_ATTEMPTS + " attempt(s)")
                .hasMessageContaining("no.such.host");

        Mockito.verify(builder, Mockito.times(SEND_PART_ATTEMPTS)).post(Mockito.any(Entity.class));
    }

    /**
     * A blip that clears before the attempts are used up must leave the send, and so the processing task, in
     * the same state as if it had never happened. See gh-5706.
     */
    @Test
    void testTransientFailureIsRetriedThenSucceeds() throws Exception {
        final Path path = Files.createTempFile("test", "test");
        Files.writeString(path, "TestFileTransferService");
        final FileDescriptor fileDescriptor = new FileDescriptor(
                System.currentTimeMillis(),
                1,
                FileHashUtil.hash(path));

        final Response okResponse = Mockito.mock(Response.class);
        Mockito.when(okResponse.getStatus()).thenReturn(Status.OK.getStatusCode());

        final Invocation.Builder builder = Mockito.mock(Invocation.Builder.class);
        Mockito.when(builder.header(Mockito.anyString(), Mockito.any())).thenReturn(builder);
        Mockito.when(builder.post(Mockito.any(Entity.class)))
                .thenThrow(new ProcessingException(new UnknownHostException("no.such.host")))
                .thenReturn(okResponse);
        final WebTarget flakyTarget = Mockito.mock(WebTarget.class);
        Mockito.when(flakyTarget.request()).thenReturn(builder);

        partSendingFileTransferClient(url -> flakyTarget).storePart(fileDescriptor, path, true);

        Mockito.verify(builder, Mockito.times(2)).post(Mockito.any(Entity.class));
    }

    private FileTransferClientImpl fileTransferClient() {
        return new FileTransferClientImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                executorProvider);
    }

    /**
     * A client for the node addressing {@code fetchSnapshot} overload, i.e. the one that resolves a node name to
     * an endpoint. The resolved URL is ignored so the call still lands on the in-process test resource.
     */
    private FileTransferClientImpl nodeCallingFileTransferClient() {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.asProcessingUserResult(Mockito.<Supplier<Object>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());

        return new FileTransferClientImpl(
                null,
                mockNodeService(),
                mockNodeInfo(),
                null,
                url -> getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART),
                null,
                securityContext,
                executorProvider);
    }

    /**
     * A client for {@code storePart}, configured to send to a single remote node. The resolved URL is ignored,
     * the supplied factory decides what the send lands on.
     */
    private FileTransferClientImpl partSendingFileTransferClient(final WebTargetFactory webTargetFactory)
            throws NullClusterStateException, NodeNotFoundException {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any(Runnable.class));

        final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        Mockito.when(targetNodeSetFactory.getEnabledTargetNodeSet()).thenReturn(Set.of(REMOTE_NODE));

        final PlanBConfig planBConfig = PlanBConfig
                .builder()
                .nodeList(List.of(REMOTE_NODE))
                .sendPartAttempts(SEND_PART_ATTEMPTS)
                // Keep the test quick, the delay is not what is under test.
                .sendPartRetryDelay(StroomDuration.ofMillis(1))
                .build();

        return new FileTransferClientImpl(
                () -> planBConfig,
                mockNodeService(),
                mockNodeInfo(),
                targetNodeSetFactory,
                webTargetFactory,
                null,
                securityContext,
                executorProvider);
    }

    private NodeInfo mockNodeInfo() {
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        return nodeInfo;
    }

    private NodeService mockNodeService() {
        final NodeService nodeService = Mockito.mock(NodeService.class);
        Mockito.when(nodeService.getBaseEndpointUrl(REMOTE_NODE)).thenReturn("http://remote:8080");
        Mockito.when(nodeService.getBaseEndpointUrl(THIS_NODE)).thenReturn("http://this:8080");
        return nodeService;
    }
}
