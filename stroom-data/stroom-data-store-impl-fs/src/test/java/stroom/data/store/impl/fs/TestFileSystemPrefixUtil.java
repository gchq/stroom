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

package stroom.data.store.impl.fs;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemPrefixUtil {
    @Test
    void testPadId() {
        assertThat(FsPrefixUtil.padId(null)).isEqualTo("000");
        assertThat(FsPrefixUtil.padId(0L)).isEqualTo("000");
        assertThat(FsPrefixUtil.padId(1L)).isEqualTo("001");
        assertThat(FsPrefixUtil.padId(1001L)).isEqualTo("001001");
    }

//    @Test
//    void testBuildIdPath() {
//        assertThat(FsPrefixUtil.buildIdPath("000000")).isEqualTo("000");
//        assertThat(FsPrefixUtil.buildIdPath(FsPrefixUtil.padId(1L))).isNull();
//        assertThat(FsPrefixUtil.buildIdPath(FsPrefixUtil.padId(1000L))).isEqualTo("001");
//        assertThat(FsPrefixUtil.buildIdPath(FsPrefixUtil.padId(9999L))).isEqualTo("009");
//    }

    @Test
    void testAppendBuildIdPath() {
        final Path root = Paths.get("");
        assertThat(FsPrefixUtil.appendIdPath(root, "000000")).isEqualTo(root.resolve("000"));
        assertThat(FsPrefixUtil.appendIdPath(root, FsPrefixUtil.padId(1L))).isEqualTo(root);
        assertThat(FsPrefixUtil.appendIdPath(root, FsPrefixUtil.padId(1000L))).isEqualTo(root.resolve("001"));
        assertThat(FsPrefixUtil.appendIdPath(root, FsPrefixUtil.padId(9999L))).isEqualTo(root.resolve("009"));
        assertThat(FsPrefixUtil.appendIdPath(root, FsPrefixUtil.padId(1000000L))).isEqualTo(root.resolve("001").resolve("000"));
        assertThat(FsPrefixUtil.appendIdPath(root, 0)).isEqualTo(root);
        assertThat(FsPrefixUtil.appendIdPath(root, 1L)).isEqualTo(root);
        assertThat(FsPrefixUtil.appendIdPath(root, 1000L)).isEqualTo(root.resolve("001"));
        assertThat(FsPrefixUtil.appendIdPath(root, 9999L)).isEqualTo(root.resolve("009"));
        assertThat(FsPrefixUtil.appendIdPath(root, 1000000L)).isEqualTo(root.resolve("001").resolve("000"));
    }
}
