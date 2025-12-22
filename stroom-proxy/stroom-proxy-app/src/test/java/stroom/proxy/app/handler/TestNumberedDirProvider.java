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

import stroom.proxy.app.handler.NumberedDirProvider.DirId;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.base.Strings;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNumberedDirProvider extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNumberedDirProvider.class);

    @Test
    void test() throws Exception {
        final Path dir = Files.createTempDirectory("test");

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000004");

        FileUtil.deleteContents(dir);

        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000005");

        FileUtil.deleteContents(dir);

        numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");
    }

    @Test
    void testBadPaths(@TempDir final Path dir) throws Exception {

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");

        Files.createFile(dir.resolve("aBadFile"));
        Files.createDirectory(dir.resolve("aBadDir"));

        numberedDirProvider = new NumberedDirProvider(dir);
        // bad items ignored
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");
    }

    @Test
    void testMissingItems(@TempDir final Path dir) throws Exception {

        NumberedDirProvider numberedDirProvider = new NumberedDirProvider(dir);
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000001");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000002");
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000003");

        Files.delete(dir.resolve("0000000002"));

        numberedDirProvider = new NumberedDirProvider(dir);
        // bad items ignored
        assertThat(numberedDirProvider.get().getFileName().toString())
                .isEqualTo("0000000004");
    }

    @Test
    void testDirIdComparator() {
        final List<DirId> dirIds = Stream.of(
                        new DirId(null, 5),
                        new DirId(null, 10),
                        new DirId(null, 2),
                        new DirId(null, 7))
                .sorted(NumberedDirProvider.DIR_ID_COMPARATOR)
                .toList();

        Assertions.assertThat(dirIds.stream()
                        .map(DirId::num)
                        .toList())
                .containsExactly(2L, 5L, 7L, 10L);
    }

    @Test
    void testCreate() {
        for (int i = 0; i < 10; i++) {
            final long num = (long) Math.pow(10, i);
            final String str = Long.toString(num);
            LOGGER.info("str: {}, len: {}", str, str.length());
            final String expected = Strings.padStart(str, 10, '0');
            final String actual = NumberedDirProvider.create(num);
            Assertions.assertThat(actual)
                    .isEqualTo(expected);
        }
    }

    @Disabled // Manual perf test only
    @Test
    void testCreatePerf() {

        final TimedCase timedCase1 = TimedCase.of("create1", (round, iterations) -> {
            for (long i = 0; i < iterations; i++) {
                final String str = create1(i);
                //noinspection ConstantValue
                if (str == null) {
                    throw new RuntimeException("null");
                }
            }
        });

        final TimedCase timedCase2 = TimedCase.of("create2", (round, iterations) -> {
            for (long i = 0; i < iterations; i++) {
                final String str = create2(i);
                if (str == null) {
                    throw new RuntimeException("null");
                }
            }
        });

        final TimedCase timedCase3 = TimedCase.of("create3", (round, iterations) -> {
            for (long i = 0; i < iterations; i++) {
                final String str = create3(i);
                if (str == null) {
                    throw new RuntimeException("null");
                }
            }
        });

        final TimedCase timedCase4 = TimedCase.of("create4", (round, iterations) -> {
            for (long i = 0; i < iterations; i++) {
                final String str = create4(i);
                if (str == null) {
                    throw new RuntimeException("null");
                }
            }
        });

        TestUtil.comparePerformance(
                5,
                10_000_000L,
                LOGGER::info,
                timedCase1,
                timedCase2,
                timedCase3,
                timedCase4);
    }

    private static String create1(final long num) {
        return Strings.padStart(Long.toString(num), 10, '0');
    }

    private static String create2(final long num) {
        if (num == 0) {
            return "0000000000";
        } else {
            final int length = (int) (Math.log10(num) + 1);
            return switch (length) {
                case 0 -> "0000000000";
                case 1 -> "000000000" + num;
                case 2 -> "00000000" + num;
                case 3 -> "0000000" + num;
                case 4 -> "000000" + num;
                case 5 -> "00000" + num;
                case 6 -> "0000" + num;
                case 7 -> "000" + num;
                case 8 -> "00" + num;
                case 9 -> "0" + num;
                case 10 -> "" + num;
                default -> throw new IllegalArgumentException("num is too big");
            };
        }
    }

    private static String create3(final long num) {
        if (num == 0) {
            return "0000000000";
        } else {
            final String str = String.valueOf(num);
            final int len = str.length();
            return switch (len) {
                case 0 -> "0000000000";
                case 1 -> "000000000" + str;
                case 2 -> "00000000" + str;
                case 3 -> "0000000" + str;
                case 4 -> "000000" + str;
                case 5 -> "00000" + str;
                case 6 -> "0000" + str;
                case 7 -> "000" + str;
                case 8 -> "00" + str;
                case 9 -> "0" + str;
                default -> str;
            };
        }
    }

    private static String create4(final long num) {
        final String str = String.valueOf(num);
        final int len = str.length();
        return switch (len) {
            case 0 -> "0000000000";
            case 1 -> "000000000" + str;
            case 2 -> "00000000" + str;
            case 3 -> "0000000" + str;
            case 4 -> "000000" + str;
            case 5 -> "00000" + str;
            case 6 -> "0000" + str;
            case 7 -> "000" + str;
            case 8 -> "00" + str;
            case 9 -> "0" + str;
            default -> str;
        };
    }
}
