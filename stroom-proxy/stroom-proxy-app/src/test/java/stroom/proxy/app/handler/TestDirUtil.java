/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.proxy.app.handler.DirUtil.Mode;
import stroom.test.common.DirectorySnapshot;
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
import static stroom.proxy.app.handler.DirUtil.getMaxDirId;

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

        assertThat(getMaxDirId(rootDir))
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
        assertThat(path.getParent())
                .isDirectory();

        path = DirUtil.createPath(rootDir, 1_001_001L);
        DirUtil.ensureDirExists(path);
        FileUtil.deleteDir(path); // Delete the dir but the parents will remain
        assertThat(path.getParent())
                .isDirectory();

        path = DirUtil.createPath(rootDir, 1_001_001_001L);
        DirUtil.ensureDirExists(path);
        FileUtil.deleteDir(path); // Delete the dir but the parents will remain
        assertThat(path.getParent())
                .isDirectory();

        LOGGER.info("Snapshot:\n{}", DirectorySnapshot.of(rootDir));

        // Incomplete path, so 0 is lowest possible value
        assertThat(DirUtil.getMinDirId(rootDir))
                .isEqualTo(0);

        // Incomplete path, so 1_001_001_999L is highest possible value
        assertThat(getMaxDirId(rootDir))
                .isEqualTo(1_001_001_999L);
    }

    @Test
    void testMaxId_missing() {
        final Path path = Path.of("/doesntExist");
        assertThat(path)
                .doesNotExist();

        Assertions.assertThatThrownBy(() ->
                        getMaxDirId(path))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void testMinId_missing() {
        final Path path = Path.of("/doesntExist");
        assertThat(path)
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
                    return new MinMax(DirUtil.getMinDirId(dir), getMaxDirId(dir));
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
    Stream<DynamicTest> testMinMax2(@TempDir final Path dir) {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(MinMax.class)
                .withTestFunction(testCase -> {
                    final List<String> pathStrings = testCase.getInput();
                    for (final String pathStr : pathStrings) {
                        DirUtil.ensureDirExists(dir.resolve(Path.of(pathStr)));
                    }
                    return new MinMax(DirUtil.getMinDirId(dir), getMaxDirId(dir));
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> {
                    FileUtil.deleteContents(dir);
                })
                .addCase(List.of(), new MinMax(0, 0))
                .addCase(List.of("0"), new MinMax(0, 999))
                .addCase(List.of(
                        "0/001",
                        "0/999"), new MinMax(1, 999))
                .addCase(List.of(
                        "0/100",
                        "1/100/100200"), new MinMax(100, 100_200))
                .addCase(List.of(
                        "1/050/",
                        "1/100/100200",
                        "1/100/100201",
                        "1/200/"), new MinMax(50_000, 200_999))
                .addCase(List.of(
                        "0foo",
                        "0/",
                        "1foo",
                        "1/100/100200",
                        "1/100/100201",
                        "2foo",
                        "2/",
                        "3foo"), new MinMax(0, 999_999_999))
                .addCase(List.of(
                        "0/",
                        "1/100/100200",
                        "1/100/100201",
                        "2/"), new MinMax(0, 999_999_999))
                .addCase(List.of(
                        "1/101/101200",
                        "1/102/102201"), new MinMax(101_200, 102_201))
                .addCase(List.of("0/foo"), new MinMax(0, 999))
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
                .addCase(2_000L, Path.of("1/002/002000"))
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
                    assertThat(isValid2)
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
                    assertThat(isValid2)
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

    @TestFactory
    Stream<DynamicTest> testGetIdFromIncompletePath() {
        final Path rootDir = Path.of("foo")
                .resolve("bar");
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(MinMax.class)
                .withTestFunction(testCase -> {
                    final String pathStr = testCase.getInput();
                    final Path path = NullSafe.isNonBlankString(pathStr)
                            ? rootDir.resolve(pathStr)
                            : rootDir;
                    final Long minId = DirUtil.getIdFromIncompleteBranch(rootDir, path, Mode.MIN);
                    final Long maxId = DirUtil.getIdFromIncompleteBranch(rootDir, path, Mode.MAX);
                    return new MinMax(minId, maxId);
                })
                .withSimpleEqualityAssertion()
                .addCase(null, new MinMax(null, null))
                .addCase("foo", new MinMax(null, null))
                .addCase("0", new MinMax(0L, 999L))
                .addCase("0/foo", new MinMax(0L, 999L))
                .addCase("0/1", new MinMax(0L, 999L))
                .addCase("1", new MinMax(1_000L, 999_999L))
                .addCase("1/123", new MinMax(123_000L, 123_999L))
                .addCase("1/123/foo", new MinMax(123_000L, 123_999L))
                .addCase("1/foo", new MinMax(1_000L, 999_999L))
                .addCase("2/123", new MinMax(123_000_000L, 123_999_999L))
                .addCase("2/123/456", new MinMax(123_456_000L, 123_456_999L))
                .addCase("3", new MinMax(1_000_000_000L, 999_999_999_999L))
                .addCase("3/123", new MinMax(123_000_000_000L, 123_999_999_999L))
                .addCase("3/123/foo", new MinMax(123_000_000_000L, 123_999_999_999L))
                .addCase("3/123/456", new MinMax(123_456_000_000L, 123_456_999_999L))
                .addCase("3/123/456/789", new MinMax(123_456_789_000L, 123_456_789_999L))
                .addCase("3/123/456/789/foo", new MinMax(123_456_789_000L, 123_456_789_999L))
                .addCase("3/001/001/001", new MinMax(1_001_001_000L, 1_001_001_999L))

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetNumberInDir() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(long.class)
                .withSingleArgTestFunction(DirUtil::getNumberInDir)
                .withSimpleEqualityAssertion()
                .addCase(0L, 0L)
                .addCase(1L, 1L)
                .addCase(999L, 999L)
                .addCase(1000L, 0L)
                .addCase(1001L, 1L)
                .addCase(1999L, 999L)
                .addCase(2000L, 0L)
                .addCase(2001L, 1L)
                .addCase(123456789L, 789L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetIdInNextBlock() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(long.class)
                .withSingleArgTestFunction(DirUtil::getIdInNextBlock)
                .withSimpleEqualityAssertion()
                .addCase(0L, 1000L)
                .addCase(1L, 1000L)
                .addCase(999L, 1000L)
                .addCase(1000L, 2000L)
                .addCase(1999L, 2000L)
                .addCase(9999L, 10000L)
                .build();
    }


    // --------------------------------------------------------------------------------


    private record MinMax(Long min, Long max) {

        private MinMax(final long min, final long max) {
            this(Long.valueOf(min), Long.valueOf(max));
        }

        private MinMax(final Long min, final Long max) {
            this.min = min;
            this.max = max;
        }
    }
}
