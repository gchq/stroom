package stroom.test.common.util;

import stroom.util.ConsoleColour;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiffUtil {

    public static void diff(final Path file1,
                     final Path file2,
                     final Consumer<String> diffLineConsumer,
                     final boolean colouredOutput,
                     final int contextLines) throws IOException {

        final String string1 = Files.readString(file1);
        final String string2 = Files.readString(file2);

        diff(string1, string2, diffLineConsumer, colouredOutput, contextLines);
    }

    public static void diff(final String string1,
                            final String string2,
                            final Consumer<String> diffLineConsumer,
                            final boolean colouredOutput,
                            final int contextLines) {

        final List<String> lines1 = string1.lines().collect(Collectors.toList());
        final List<String> lines2 = string2.lines().collect(Collectors.toList());

        final Patch<String> patch = DiffUtils.diff(lines1, lines2);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                string1,
                string2,
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
                }
                diffLineConsumer.accept(diffLine);
            });
        }
    }
}
