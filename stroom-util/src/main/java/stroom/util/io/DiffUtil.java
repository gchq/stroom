package stroom.util.io;

import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiffUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffUtil.class);

    public static boolean unifiedDiff(final Path originalFile,
                                      final Path revisedFile,
                                      final boolean colouredOutput,
                                      final int contextLines,
                                      final Consumer<List<String>> diffLinesConsumer) {

        final String originalContent = getFileAsString(originalFile);
        final String revisedContent = getFileAsString(revisedFile);

        return diff(
                originalFile.toAbsolutePath().normalize().toString(),
                revisedFile.toAbsolutePath().normalize().toString(),
                originalContent,
                revisedContent,
                colouredOutput, contextLines, diffLinesConsumer
        );
    }

    public static boolean unifiedDiff(final String originalContent,
                                      final String revisedContent,
                                      final boolean colouredOutput,
                                      final int contextLines,
                                      final Consumer<List<String>> diffLinesConsumer) {


        // We have no files so used fixed words to appear in the diff
        return diff(
                "Original",
                "Revised",
                originalContent,
                revisedContent,
                colouredOutput, contextLines, diffLinesConsumer
        );
    }

    private static String getFileAsString(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error opening file {}: {}",
                    file.toAbsolutePath().normalize(), e.getMessage()), e);
        }
    }

    private static boolean diff(final String file1Path,
                                final String file2Path,
                                final String string1,
                                final String string2,
                                final boolean colouredOutput,
                                final int contextLines,
                                final Consumer<List<String>> diffLinesConsumer) {

        final List<String> lines1 = string1.lines()
                .collect(Collectors.toList());
        final List<String> lines2 = string2.lines()
                .collect(Collectors.toList());

        final Patch<String> patch = DiffUtils.diff(lines1, lines2);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                file1Path,
                file2Path,
                lines1,
                patch,
                contextLines);

        if (!unifiedDiff.isEmpty() && diffLinesConsumer != null) {
            if (colouredOutput) {
                final List<String> colouredLines = unifiedDiff.stream()
                        .map(diffLine -> {

                            final ConsoleColour lineColour;
                            if (diffLine.startsWith("+")) {
                                lineColour = ConsoleColour.GREEN;
                            } else if (diffLine.startsWith("-")) {
                                lineColour = ConsoleColour.RED;
                            } else {
                                lineColour = ConsoleColour.NO_COLOUR;
                            }
                            return ConsoleColour.colourise(diffLine, lineColour);
                        })
                        .collect(Collectors.toList());

                diffLinesConsumer.accept(colouredLines);
            } else {
                diffLinesConsumer.accept(unifiedDiff);
            }
        }
        return !unifiedDiff.isEmpty();
    }
}
