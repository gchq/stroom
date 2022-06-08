package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.string.StringIdUtil;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestKafkaEventConsumer {

    @Test
    void test() throws IOException {
        final Path dir = Files.createTempDirectory("stroom");

        final Path messageFile = dir.resolve("messages.dat");

        final MessageStore messageStore = new MessageStore(messageFile);

        for (int i = 0; i < 10; i++) {
            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put("Feed", "Test");
            attributeMap.put("Type", "Raw Events");
            messageStore.consume(attributeMap, outputStream -> {
                try {
                    outputStream.write("test".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        messageStore.close();

        final Path zipFile = dir.resolve("messages.zip");
        messageStore.toZip(zipFile);

        try (final ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            for (int i = 0; i < 10; i++) {
                final String idString = StringIdUtil.idToString(i);
                final ZipEntry zipEntry = zipInputStream.getNextEntry();
                assertThat(zipEntry.getName()).isEqualTo(idString + ".meta");

                final AttributeMap attributeMap = new AttributeMap();
                AttributeMapUtil.read(zipInputStream, attributeMap);
                assertThat(attributeMap.size()).isEqualTo(2);
                assertThat(attributeMap.get("Feed")).isEqualTo("Test");
                assertThat(attributeMap.get("Type")).isEqualTo("Raw Events");

                final ZipEntry zipEntry2 = zipInputStream.getNextEntry();
                assertThat(zipEntry2.getName()).isEqualTo(idString + ".dat");
                final byte[] bytes = zipInputStream.readAllBytes();
                assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("test");
            }

            assertThat(zipInputStream.getNextEntry()).isNull();
        }
    }
}
