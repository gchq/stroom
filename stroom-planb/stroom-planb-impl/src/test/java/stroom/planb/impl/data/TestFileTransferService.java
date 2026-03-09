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

package stroom.planb.impl.data;

import stroom.docref.DocRef;
import stroom.planb.shared.PlanBDoc;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.io.StreamUtil;
import stroom.util.zip.ZipUtil;

import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileTransferService extends AbstractResourceTest<FileTransferResource> {

    @Mock
    private FileTransferService fileTransferService;

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
                null);

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
                null);

        Mockito
                .doAnswer(invocation -> {
                    final SnapshotRequest request = invocation.getArgument(0);

                    // If we already have a snapshot for the current write time then don't create a snapshot and just
                    // return an error.
                    if (request.getCurrentSnapshotTime() != null &&
                        Objects.equals(lastWriteTime.toEpochMilli(), request.getCurrentSnapshotTime())) {
                        throw new NotModifiedException();
                    }

                    assertThat(request.getPlanBDocRef()).isEqualTo(planBDocRef);
                    assertThat(request.getEffectiveTime()).isEqualTo(requestTime);
                    return null;
                })
                .when(fileTransferService).checkSnapshotStatus(
                        Mockito.any(SnapshotRequest.class));
        Mockito
                .doAnswer(invocation -> {
                    final SnapshotRequest request = invocation.getArgument(0);
                    final OutputStream outputStream = invocation.getArgument(1);

                    assertThat(request.getPlanBDocRef()).isEqualTo(planBDocRef);
                    assertThat(request.getEffectiveTime()).isEqualTo(requestTime);
                    outputStream.write(Files.readAllBytes(zipFile));
                    return null;
                })
                .when(fileTransferService).fetchSnapshot(
                        Mockito.any(SnapshotRequest.class),
                        Mockito.any(OutputStream.class));

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
}
