/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.io;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimplePathCreator {

    @Test
    void testReplaceFileName(@TempDir final Path tempDir) {
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        assertThat(pathCreator.replaceFileName("${fileStem}.txt", "test.tmp")).isEqualTo("test.txt");

        assertThat(pathCreator.replaceFileName("${fileStem}", "test.tmp")).isEqualTo("test");

        assertThat(pathCreator.replaceFileName("${fileStem}", "test")).isEqualTo("test");

        assertThat(pathCreator.replaceFileName("${fileExtension}", "test.tmp")).isEqualTo("tmp");

        assertThat(pathCreator.replaceFileName("${fileExtension}", "test")).isEqualTo("");

        assertThat(pathCreator.replaceFileName("${fileName}.txt", "test.tmp")).isEqualTo("test.tmp.txt");
    }

    @Test
    void testFindVars(@TempDir final Path tempDir) {
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final String[] vars = pathCreator.findVars("/temp/${feed}-FLAT/${pipe}_less-${uuid}/${searchId}");
        assertThat(vars.length).isEqualTo(4);
        assertThat(vars[0]).isEqualTo("feed");
        assertThat(vars[1]).isEqualTo("pipe");
        assertThat(vars[2]).isEqualTo("uuid");
        assertThat(vars[3]).isEqualTo("searchId");
    }

    @TestFactory
    Stream<DynamicTest> testContainsVars() {
        final PathCreator pathCreator = new SimplePathCreator(() -> null, () -> null);
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(pathCreator::containsVars)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("foo", false)
                .addCase("$", false)
                .addCase("${", false)
                .addCase("}", false)
                .addCase("}${", false)
                .addCase("${foo}", true)
                .addCase("foo${foo}foo", true)
                .addCase("${foo}foo", true)
                .addCase("foo${foo}", true)
                .addCase("${foo}${bar}", true)
                .build();
    }

    @Test
    void testReplaceTime(@TempDir final Path tempDir) {
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 8, 20, 13, 17, 22, 2111444, ZoneOffset.UTC);

        String path = "${feed}/${year}/${year}-${month}/${year}-${month}-${day}/${pathId}/${id}";

        // Replace pathId variable with path id.
        path = pathCreator.replace(path, "pathId", () -> "1234");
        // Replace id variable with file id.
        path = pathCreator.replace(path, "id", () -> "5678");

        assertThat(path).isEqualTo("${feed}/${year}/${year}-${month}/${year}-${month}-${day}/1234/5678");

        path = pathCreator.replaceTimeVars(path, zonedDateTime);

        assertThat(path).isEqualTo("${feed}/2018/2018-08/2018-08-20/1234/5678");
    }
}
