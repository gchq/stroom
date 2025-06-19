package stroom.proxy.app.handler;

import stroom.test.common.TestUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.TypeLiteral;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDirUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDirUtil.class);

    @Test
    void testBadFilesAndDirs(@TempDir final Path rootDir) throws IOException {
        final List<Long> nums = List.of(
                1L,
                10L,
                99L,
                100L,
                999L);

        final List<Path> relPaths = new ArrayList<>();
        for (final Long num : nums) {
            final Path path = DirUtil.createPath(rootDir, num);
            DirUtil.ensureDirExists(path);
            relPaths.add(rootDir.relativize(path));
        }

        LOGGER.info("relPaths:\n{}", relPaths.stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n")));

        DirUtil.ensureDirExists(rootDir.resolve("badDir"));
        FileUtil.touch(rootDir.resolve("badFile"));

        assertThat(DirUtil.getMaxDirId(rootDir))
                .isEqualTo(999);
    }

    @Test
    void testEmptyDirs(@TempDir final Path rootDir) throws IOException {
        final List<Long> nums = List.of(
                99L,
                1001L,
                1003L);

        final List<Path> relPaths = new ArrayList<>();
        for (final Long num : nums) {
            final Path path = DirUtil.createPath(rootDir, num);
            DirUtil.ensureDirExists(path);
            relPaths.add(rootDir.relativize(path));
        }

        LOGGER.info("relPaths:\n{}", relPaths.stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n")));

        Path path = DirUtil.createPath(rootDir, 99L);
        DirUtil.ensureDirExists(path);
        FileUtil.deleteDir(path); // Delete the dir but the parents will remain
        Assertions.assertThat(path.getParent())
                .isDirectory();

        path = DirUtil.createPath(rootDir, 1_001_001L);
        DirUtil.ensureDirExists(path);
        FileUtil.deleteDir(path); // Delete the dir but the parents will remain
        Assertions.assertThat(path.getParent())
                .isDirectory();

        path = DirUtil.createPath(rootDir, 1_001_001_001L);
        DirUtil.ensureDirExists(path);
        FileUtil.deleteDir(path); // Delete the dir but the parents will remain
        Assertions.assertThat(path.getParent())
                .isDirectory();

        // Min ignores empty dirs
        assertThat(DirUtil.getMinDirId(rootDir))
                .isEqualTo(1001);

        // Max throws if it finds empty dirs
        Assertions.assertThatThrownBy(
                        () -> {
                            DirUtil.getMaxDirId(rootDir);
                        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Incomplete directory ID");
    }

    @Test
    void testMaxId_missing() {
        final Path path = Path.of("/doesntExist");
        Assertions.assertThat(path)
                .doesNotExist();

        Assertions.assertThatThrownBy(() ->
                        DirUtil.getMaxDirId(path))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void testMinId_missing() {
        final Path path = Path.of("/doesntExist");
        Assertions.assertThat(path)
                .doesNotExist();

        Assertions.assertThatThrownBy(() ->
                        DirUtil.getMinDirId(path))
                .isInstanceOf(UncheckedIOException.class);
    }

    @TestFactory
    Stream<DynamicTest> testMakeSafeName() {
        final String allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(DirUtil::makeSafeName)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase("foo", "foo")
                .addCase("foo=bar", "foo_bar")
                .addCase("/foo/bar/", "_foo_bar_")
                .addCase(allowedChars, allowedChars)
                .addCase(
                        "foo!\"£$%^&*()_+{}[];:'@<>?,./~`¬ ",
                        "foo______________________________")
                .withActualOutputConsumer(LOGGER::info)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMinMax(@TempDir final Path dir) {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Long>>() {
                })
                .withOutputType(MinMax.class)
                .withTestFunction(testCase -> {
                    FileUtil.deleteContents(dir);
                    final List<Long> nums = testCase.getInput();
                    for (final Long num : nums) {
                        final Path path = DirUtil.createPath(dir, num);
                        DirUtil.ensureDirExists(path);
                    }
                    return new MinMax(DirUtil.getMinDirId(dir), DirUtil.getMaxDirId(dir));
                })
                .withSimpleEqualityAssertion()
                .addCase(List.of(), new MinMax(0, 0))
                .addCase(List.of(0L), new MinMax(0, 0))
                .addCase(List.of(1L), new MinMax(1, 1))
                .addCase(List.of(0L, 10L), new MinMax(0, 10))
                .addCase(List.of(1_000L, 1_000_000L), new MinMax(1_000, 1_000_000))
                .addCase(List.of(
                                1L, 1_000L, 1_000_000L, 1_000_000_000L),
                        new MinMax(1, 1_000_000_000))
                .addCase(List.of(
                                111_555_777L,
                                333_555_777L,
                                333_111_777L,
                                333_555_777L,
                                333_555_111L,
                                333_555_777L),
                        new MinMax(111_555_777, 333_555_777))
                .addCase(List.of(
                                111_222L,
                                333_555L,
                                111_555_777L,
                                333_555_111L,
                                333_555_777L,
                                555_123L),
                        new MinMax(111_222, 333_555_777))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreatePath(@TempDir final Path dir) {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(Path.class)
                .withTestFunction(testCase -> {
                    final long num = testCase.getInput();
                    return DirUtil.createPath(dir, num);
                })
                .withAssertions(outcome -> {
                    final Path actualOutput = outcome.getActualOutput();
                    final Path expected = dir.resolve(outcome.getExpectedOutput());
                    assertThat(actualOutput)
                            .isEqualTo(expected);
                })
                .addThrowsCase(-1L, IllegalArgumentException.class)
                .addCase(0L, Path.of("0/000"))
                .addCase(1L, Path.of("0/001"))
                .addCase(999L, Path.of("0/999"))
                .addCase(1_000L, Path.of("1/001/001000"))
                .addCase(1_999L, Path.of("1/001/001999"))
                .addCase(999_999L, Path.of("1/999/999999"))
                .addCase(1_000_000L, Path.of("2/001/000/001000000"))
                .addCase(12_345_000L, Path.of("2/012/345/012345000"))
                .addCase(12_345_678L, Path.of("2/012/345/012345678"))
                .addCase(333_555_777L, Path.of("2/333/555/333555777"))
                .addCase(Long.MAX_VALUE, Path.of("6/009/223/372/036/854/775/009223372036854775807"))
                .withActualOutputConsumer(LOGGER::info)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsValidDepth() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(DirUtil::isValidDepthPart)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase("X", false)
                .addCase("foo", false)
                .addCase("123", false)
                .addCase("1", true)
                .addCase("2", true)
                .addCase("3", true)
                .addCase("4", true)
                .addCase("5", true)
                .addCase("6", true) // This is max depth
                .addCase("7", false)
                .addCase("8", false)
                .addCase("9", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsValidLeafPath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Path.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final Path path = testCase.getInput();
                    final boolean isValid = DirUtil.isValidLeafPath(path);

                    final Path longerPath = NullSafe.get(path, aPath -> Path.of("/tmp/foo/bar").resolve(aPath));
                    final boolean isValid2 = DirUtil.isValidLeafPath(longerPath);
                    Assertions.assertThat(isValid2)
                            .withFailMessage(() -> LogUtil.message(
                                    "Different outcomes for path: {}, longerPath: {}, isValid: {}, isValid2: {}",
                                    path, longerPath, isValid, isValid2))
                            .isEqualTo(isValid);
                    return isValid;
                })
                .withSimpleEqualityAssertion()
                // Bad
                .addCase(null, false)
                .addCase(Path.of("foo"), false)
                .addCase(Path.of("0"), false)
                .addCase(Path.of("0/foo"), false)
                .addCase(Path.of("1/001"), false)
                .addCase(Path.of("1/001/foo"), false)
                .addCase(Path.of("1/123/456"), false)
                .addCase(Path.of("2/123/456/456789"), false)
                .addCase(Path.of("4/123/456/789/123456789321"), false)
                .addCase(Path.of("123/456/789/123456789321"), false)
                // Good
                .addCase(Path.of("0/001"), true)
                .addCase(Path.of("1/123/123456"), true)
                .addCase(Path.of("2/123/456/123456789"), true)
                .addCase(Path.of("3/123/456/789/123456789321"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsValidLeafOrBranchPath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Path.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final Path path = testCase.getInput();
                    final boolean isValid = DirUtil.isValidLeafOrBranchPath(path);

                    final Path longerPath = NullSafe.get(path, aPath -> Path.of("/tmp/foo/bar").resolve(aPath));
                    final boolean isValid2 = DirUtil.isValidLeafOrBranchPath(longerPath);
                    Assertions.assertThat(isValid2)
                            .withFailMessage(() -> LogUtil.message(
                                    "Different outcomes for path: {}, longerPath: {}, isValid: {}, isValid2: {}",
                                    path, longerPath, isValid, isValid2))
                            .isEqualTo(isValid);
                    return isValid;
                })
                .withSimpleEqualityAssertion()
                // Bad
                .addCase(null, false)
                .addCase(Path.of("foo"), false)
                .addCase(Path.of("0/foo"), false)
                .addCase(Path.of("1/001/foo"), false)
                .addCase(Path.of("1/123/456"), false)
                .addCase(Path.of("2/123/456/456789"), false)
                .addCase(Path.of("4/123/456/789/123456789321"), false)
                .addCase(Path.of("123/456/789/123456789321"), false)
                // Good
                .addCase(Path.of("0"), true)
                .addCase(Path.of("0/001"), true)
                .addCase(Path.of("1/001"), true)
                .addCase(Path.of("1"), true)
                .addCase(Path.of("1/123"), true)
                .addCase(Path.of("1/123/123456"), true)
                .addCase(Path.of("2"), true)
                .addCase(Path.of("2/123"), true)
                .addCase(Path.of("2/123/456"), true)
                .addCase(Path.of("2/123/456/123456789"), true)
                .addCase(Path.of("3"), true)
                .addCase(Path.of("3/123"), true)
                .addCase(Path.of("3/123/456"), true)
                .addCase(Path.of("3/123/456/789"), true)
                .addCase(Path.of("3/123/456/789/123456789321"), true)
                .build();
    }


    // --------------------------------------------------------------------------------


    private record MinMax(long min, long max) {

    }
}
