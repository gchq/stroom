package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestPartsPathUtil {
    @Test
    public void test() throws IOException {
        Path uniqueTestDir = Files.createTempDirectory("stroom");

        final Path zipFile = uniqueTestDir.resolve("test.zip");
        final Path partsDir = PartsPathUtil.createPartsDir(zipFile);
        Assert.assertTrue(PartsPathUtil.isPartsDir(partsDir));
        final Path parentZipFile = PartsPathUtil.createParentPartsZipFile(partsDir);
        Assert.assertEquals(zipFile, parentZipFile);

        final Path part = PartsPathUtil.createPart(partsDir, zipFile, "001");
        Assert.assertTrue(PartsPathUtil.isPart(part));

        try {
            PartsPathUtil.checkPath(partsDir.getFileName().toString());
            Assert.fail();
        } catch (final IOException e) {
        }
        try {
            PartsPathUtil.checkPath(part.getFileName().toString());
            Assert.fail();
        } catch (final IOException e) {
        }
    }
}
