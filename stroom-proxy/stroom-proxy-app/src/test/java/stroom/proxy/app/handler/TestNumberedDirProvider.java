package stroom.proxy.app.handler;

import stroom.proxy.app.handler.NumberedDirProvider.DirId;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNumberedDirProvider extends StroomUnitTest {

    @Test
    void test() throws Exception {
        final Path dir = Files.createTempDirectory("test");

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000004");

        FileUtil.deleteContents(dir);

        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000005");

        FileUtil.deleteContents(dir);

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");
    }

    @Test
    void testBadPaths(@TempDir Path dir) throws Exception {

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");

        Files.createFile(dir.resolve("aBadFile"));
        Files.createDirectory(dir.resolve("aBadDir"));

        numberedDirProvider = new NumberedDirProvider(dir);
        // bad items ignored
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");
    }

    @Test
    void testMissingItems(@TempDir Path dir) throws Exception {

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");

        Files.delete(dir.resolve("0000000002"));

        numberedDirProvider = new NumberedDirProvider(dir);
        // bad items ignored
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000004");
    }

    @Test
    void testDirIdComparator() {
        final List<DirId> dirIds = Stream.of(
                        new DirId(null, 5),
                        new DirId(null, 10),
                        new DirId(null, 2),
                        new DirId(null, 7))
                .sorted(NumberedDirProvider.DIR_ID_COMPARATOR)
                .toList();

        Assertions.assertThat(dirIds.stream()
                        .map(DirId::num)
                        .toList())
                .containsExactly(2L, 5L, 7L, 10L);
    }
}
