package stroom.planb.impl.data;

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

import static org.assertj.core.api.Assertions.assertThat;

class TestFileTransferService extends AbstractResourceTest<FileTransferResource> {

    @Mock
    private FileTransferService fileTransferService;

    @Test
    void testStorePartRemotely() throws IOException {
        final Path path = Files.createTempFile("test", "test");
        Files.writeString(path, "TestFileTransferService");
        final String inputFileHash = FileHashUtil.hash(path);
        final long inputCreateTime = System.currentTimeMillis();
        final FileDescriptor fileDescriptor = new FileDescriptor(inputCreateTime, 1, inputFileHash);
        final FileTransferClientImpl fileTransferClient = new FileTransferClientImpl(
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
                    final InputStream inputStream = invocation.getArgument(4);
                    assertThat(createTime).isEqualTo(inputCreateTime);
                    assertThat(metaId).isEqualTo(1);
                    assertThat(fileHash).isEqualTo(inputFileHash);
                    assertThat(StreamUtil.streamToString(inputStream)).isEqualTo("TestFileTransferService");
                    return null;
                })
                .when(fileTransferService).receivePart(
                        Mockito.anyLong(),
                        Mockito.anyLong(),
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(InputStream.class));

        final WebTarget webTarget = getWebTarget(FileTransferResource.SEND_PART_PATH_PART);
        assertThat(fileTransferClient.storePartRemotely(webTarget, fileDescriptor, path)).isTrue();
    }

    @Test
    void testFetchSnapshot() throws IOException {
        final Path sourceDir = Files.createTempDirectory("test");
        final Path testFile = sourceDir.resolve("test.txt");
        Files.writeString(testFile, "TestFileTransferService");
        final Path zipFile = sourceDir.resolve("test.zip");
        ZipUtil.zip(zipFile, sourceDir);
        final Path targetDir = Files.createTempDirectory("test");

        final String mapName = "TestMap";
        final long requestTime = System.currentTimeMillis();
        final FileTransferClientImpl fileTransferClient = new FileTransferClientImpl(
                null,
                null,
                null,
                null,
                null,
                null);

        Mockito
                .doAnswer(invocation -> {
                    final SnapshotRequest request = invocation.getArgument(0);
                    final OutputStream outputStream = invocation.getArgument(1);
                    assertThat(request.getMapName()).isEqualTo(mapName);
                    assertThat(request.getEffectiveTime()).isEqualTo(requestTime);
                    outputStream.write(Files.readAllBytes(zipFile));
                    return null;
                })
                .when(fileTransferService).fetchSnapshot(
                        Mockito.any(SnapshotRequest.class),
                        Mockito.any(OutputStream.class));

        final SnapshotRequest request = new SnapshotRequest(mapName, requestTime);
        final WebTarget webTarget = getWebTarget(FileTransferResource.FETCH_SNAPSHOT_PATH_PART);
        fileTransferClient.fetchSnapshot(webTarget, request, targetDir);

        final Path targetFile = targetDir.resolve("test.txt");
        assertThat(Files.exists(targetFile)).isTrue();
        assertThat(Files.readString(targetFile)).isEqualTo("TestFileTransferService");
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
