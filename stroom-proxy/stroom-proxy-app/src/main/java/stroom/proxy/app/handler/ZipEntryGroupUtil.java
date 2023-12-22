package stroom.proxy.app.handler;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ZipEntryGroupUtil {

    private static final ObjectMapper MAPPER = createObjectMapper();

    public static void write(final Path path, final List<ZipEntryGroup> groups) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(path)) {
            for (final ZipEntryGroup group : groups) {
                final String json = MAPPER.writeValueAsString(group);
                writer.write(json);
                writer.write("\n");
            }
        }
    }

    public static ZipEntryGroup read(final String line) throws IOException {
        return MAPPER.readValue(line, ZipEntryGroup.class);
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
