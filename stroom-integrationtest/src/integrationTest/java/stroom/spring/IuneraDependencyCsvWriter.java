package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.fail;

/**
 * This class generates a CSV file that can be visualised using the iunera Code Analysis tool.
 *
 * https://github.com/iunera/codeanalysis
 *
 */
class IuneraDependencyCsvWriter implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IuneraDependencyCsvWriter.class);

    private final Class<?> testClass;
    private Path path;
    private BufferedWriter writer;

    public IuneraDependencyCsvWriter(final Class<?> testClass) {
        this.testClass = testClass;

        try {
            path = FileUtil.getTempDir().resolve(String.format("DependencyTest_%s.csv", testClass.getSimpleName()));

            writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)));
            writer.write("creditor,debtor,amount,risk\n");

        } catch (final IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    public void write(final String creditor,
                      final String debtor,
                      final int amount,
                      final int risk) {
        try {
            writer.write(String.format("%s,%s,%d,%d\n", creditor, debtor, amount, risk));
        } catch (final IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @Override
    public void close() {
        try {
            LOGGER.info(String.format("Written Dependencies for %s to %s", testClass, path));
            writer.close();
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
    }
}
