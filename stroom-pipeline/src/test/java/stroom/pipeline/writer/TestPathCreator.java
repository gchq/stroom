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

package stroom.pipeline.writer;


import org.junit.jupiter.api.Test;
import stroom.util.test.StroomUnitTest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TestPathCreator extends StroomUnitTest {
    @Test
    void testReplaceFileName() {
        assertThat(PathCreator.replaceFileName("${fileStem}.txt", "test.tmp")).isEqualTo("test.txt");

        assertThat(PathCreator.replaceFileName("${fileStem}", "test.tmp")).isEqualTo("test");

        assertThat(PathCreator.replaceFileName("${fileStem}", "test")).isEqualTo("test");

        assertThat(PathCreator.replaceFileName("${fileExtension}", "test.tmp")).isEqualTo("tmp");

        assertThat(PathCreator.replaceFileName("${fileExtension}", "test")).isEqualTo("");

        assertThat(PathCreator.replaceFileName("${fileName}.txt", "test.tmp")).isEqualTo("test.tmp.txt");
    }

    @Test
    void testFindVars() {
        final String[] vars = PathCreator.findVars("/temp/${feed}-FLAT/${pipe}_less-${uuid}/${searchId}");
        assertThat(vars.length).isEqualTo(4);
        assertThat(vars[0]).isEqualTo("feed");
        assertThat(vars[1]).isEqualTo("pipe");
        assertThat(vars[2]).isEqualTo("uuid");
        assertThat(vars[3]).isEqualTo("searchId");
    }

    @Test
    void testReplaceTime() {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 8, 20, 13, 17, 22, 2111444, ZoneOffset.UTC);

        String path = "${feed}/${year}/${year}-${month}/${year}-${month}-${day}/${pathId}/${id}";

        // Replace pathId variable with path id.
        path = PathCreator.replace(path, "pathId", () -> "1234");
        // Replace id variable with file id.
        path = PathCreator.replace(path, "id", () -> "5678");

        assertThat(path).isEqualTo("${feed}/${year}/${year}-${month}/${year}-${month}-${day}/1234/5678");

        path = PathCreator.replaceTimeVars(path, zonedDateTime);

        assertThat(path).isEqualTo("${feed}/2018/2018-08/2018-08-20/1234/5678");
    }
}
