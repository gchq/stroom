package stroom.query.api.v2;

import stroom.test.common.ProjectPathUtil;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class TestSearchRequestSerialisation {

    @TestFactory
    Stream<DynamicTest> test() {
        final Path dir = getDir();
        final ObjectMapper objectMapper = createMapper(false);
        try (final Stream<Path> stream = Files.list(dir)) {
            final List<Path> list = stream.toList();
            return list.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                try {
                    final String json = Files.readString(path);
                    objectMapper.readValue(json, SearchRequest.class);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getDir() {
        return ProjectPathUtil
                .resolveDir("stroom-query-api")
                .resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("TestSearchRequestSerialisation");
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
//        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }
}
