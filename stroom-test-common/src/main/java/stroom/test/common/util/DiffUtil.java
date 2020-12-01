package stroom.test.common.util;

import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiffUtil {

    public static boolean unifiedDiff(final Path file1,
                                   final Path file2,
                                   final Consumer<String> diffLineConsumer,
                                   final boolean colouredOutput,
                                   final int contextLines) {

        final String string1 = getFileAsString(file1);
        final String string2 = getFileAsString(file2);

        return diff(file1, file2, string1, string2, diffLineConsumer, colouredOutput, contextLines);
    }

    private static String getFileAsString(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error opening file {}: {}",
                    file.toAbsolutePath().normalize(), e.getMessage()), e);
        }
    }

    private static boolean diff(final Path file1,
                             final Path file2,
                             final String string1,
                             final String string2,
                             final Consumer<String> diffLineConsumer,
                             final boolean colouredOutput,
                             final int contextLines) {

        final List<String> lines1 = string1.lines().collect(Collectors.toList());
        final List<String> lines2 = string2.lines().collect(Collectors.toList());

        final Patch<String> patch = DiffUtils.diff(lines1, lines2);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                file1.toAbsolutePath().toString(),
                file2.toAbsolutePath().toString(),
                lines2,
                patch,
                contextLines);

        if (!unifiedDiff.isEmpty()) {
            unifiedDiff.forEach(diffLine -> {

                final ConsoleColour lineColour;
                if (diffLine.startsWith("+")) {
                    lineColour = ConsoleColour.GREEN;
                } else if (diffLine.startsWith("-")) {
                    lineColour = ConsoleColour.RED;
                } else {
                    lineColour = ConsoleColour.NO_COLOUR;
                }

                if (colouredOutput) {
                    diffLineConsumer.accept(ConsoleColour.colourise(diffLine, lineColour));
                } else {
                    diffLineConsumer.accept(diffLine);
                }
            });
        }
        return !unifiedDiff.isEmpty();
    }
}
