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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static stroom.util.ConsoleColour.BLUE;
import static stroom.util.ConsoleColour.RED;
import static stroom.util.ConsoleColour.YELLOW;

public class TestListDbMigrations {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestListDbMigrations.class);

    private static Pattern MIGRATION_FILE_REGEX_PATTERN = Pattern.compile("^(V0?7_|V[0-9]+__).*\\.(sql|java)$");
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

        // Core is always run first so list it first
        final Comparator<String> moduleComparator = (o1, o2) -> {
            String stroomCoreModuleName = "stroom-core";

            if (Objects.equals(o1, o2)) {
                return 0;
            } else if (stroomCoreModuleName.equals(o1)) {
                return -1;
            } else if (stroomCoreModuleName.equals(o2)) {
                return 1;
            } else {
                return Comparator.<String>naturalOrder().compare(o1, o2);
            }
        };

        migrations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(moduleComparator))
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

        // Core is always run first so list it first
        Comparator<String> fileNameComparator = (name1, name2) -> {

            Pattern authMigrationPattern = Pattern.compile("^V[0-9]+__.*");

            String name1Modified = name1;
            String name2Modified = name2;

            if (authMigrationPattern.matcher(name1).matches()) {
                name1Modified = name1Modified.replaceAll("(^V|__.*)", "");
                name2Modified = name2Modified.replaceAll("(^V|__.*)", "");

                int ver1 = Integer.parseInt(name1Modified);
                int ver2 = Integer.parseInt(name2Modified);

                return Comparator.<Integer>naturalOrder().compare(ver1, ver2);

            } else {
                return Comparator.<String>naturalOrder().compare(name1, name2);
            }
        };

        try (Stream<Path> stream = Files.walk(moduleDir)) {
            stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> MIGRATION_PATH_REGEX_PATTERN.asMatchPredicate().test(path.toString()))
                    .map(path -> Tuple.of(path.getFileName().toString(), moduleDir.relativize(path)))
                    .filter(tuple -> MIGRATION_FILE_REGEX_PATTERN.asMatchPredicate().test(tuple._1()))
                    .sorted(Comparator.comparing(Tuple2::_1, fileNameComparator))
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
