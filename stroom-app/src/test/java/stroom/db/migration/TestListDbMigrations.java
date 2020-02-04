package stroom.db.migration;

import com.google.common.base.Strings;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.ColouredStringBuilder;
import stroom.util.ConsoleColour;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static stroom.util.ConsoleColour.BLUE;
import static stroom.util.ConsoleColour.RED;
import static stroom.util.ConsoleColour.YELLOW;

public class TestListDbMigrations {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestListDbMigrations.class);

    private static Pattern MIGRATION_FILE_REGEX_PATTERN = Pattern.compile("^V0?7_.*\\.(sql|java)$");
    private static Pattern MIGRATION_PATH_REGEX_PATTERN = Pattern.compile("^.*/src/main/.*$");

    Map<String, List<Tuple2<String, Path>>> migrations = new HashMap<>();

    /**
     * Finds all the v7 DB migration scripts and dumps them out in order.
     * Useful for seeing the sql and java migrations together
     */
    @Test
    void listDbMigrations() throws IOException {

        Path projectRoot = Paths.get("../").toAbsolutePath().normalize();

        try (Stream<Path> stream = Files.list(projectRoot)) {
            stream
                .filter(path -> Files.isDirectory(path))
                .filter(path -> path.getFileName().toString().startsWith("stroom-"))
                .sorted()
                .forEach(this::inspectModule);
        }

        final ColouredStringBuilder stringBuilder = new ColouredStringBuilder();
        int maxFileNameLength = migrations.values().stream()
                .flatMap(value -> value.stream()
                .map(Tuple2::_1))
                .mapToInt(String::length)
                .max()
                .orElse(60);

        migrations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    stringBuilder
                        .appendMagenta(entry.getKey())
                        .append("\n");
                    entry.getValue()
                            .forEach(tuple -> {
                        String filename = tuple._1();
                        stringBuilder.append("  ");

                        final ConsoleColour colour;
                        if (filename.endsWith(".sql")) {
                            colour = YELLOW;
                        } else if (filename.endsWith(".java")) {
                            colour = BLUE;
                        } else {
                            colour = RED;
                        }
                        stringBuilder
                                .append(Strings.padEnd(filename, maxFileNameLength, ' '), colour)
                                .append(" - ")
                                .append(tuple._2().toString(), colour)
                                .append("\n");
                    });
                    stringBuilder.append("\n");
                });
        LOGGER.info("\n{}", stringBuilder.toString());
    }

    private void inspectModule(final Path moduleDir) {

        try (Stream<Path> stream = Files.walk(moduleDir)) {
            stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> MIGRATION_PATH_REGEX_PATTERN.asMatchPredicate().test(path.toString()))
                    .map(path -> Tuple.of(path.getFileName().toString(), moduleDir.relativize(path)))
                    .filter(tuple -> MIGRATION_FILE_REGEX_PATTERN.asMatchPredicate().test(tuple._1()))
                    .sorted()
                    .forEach(tuple -> {
                        String moduleName = moduleDir.getFileName().toString();
                        migrations.computeIfAbsent(moduleName, k -> new ArrayList<>())
                                .add(tuple);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
