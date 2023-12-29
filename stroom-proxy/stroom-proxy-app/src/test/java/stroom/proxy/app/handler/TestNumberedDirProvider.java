package stroom.proxy.app.handler;

import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNumberedDirProvider {

    @Test
    void test() throws Exception {
        final Path dir = Files.createTempDirectory("test");

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000002");

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000003");
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000004");

        FileUtil.deleteContents(dir);

        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000005");

        FileUtil.deleteContents(dir);

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString()).isEqualTo("0000000002");
    }
}
