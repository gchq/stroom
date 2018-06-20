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

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemPrefixUtil {
    @Test
    void testPadId() {
        assertThat(FileSystemPrefixUtil.padId(null)).isEqualTo("000");
        assertThat(FileSystemPrefixUtil.padId(0L)).isEqualTo("000");
        assertThat(FileSystemPrefixUtil.padId(1L)).isEqualTo("001");
        assertThat(FileSystemPrefixUtil.padId(1001L)).isEqualTo("001001");
    }

    @Test
    void testBuildIdPath() {
        assertThat(FileSystemPrefixUtil.buildIdPath("000000")).isEqualTo("000");
        assertThat(FileSystemPrefixUtil.buildIdPath(FileSystemPrefixUtil.padId(1L))).isNull();
        assertThat(FileSystemPrefixUtil.buildIdPath(FileSystemPrefixUtil.padId(9999L))).isEqualTo("009");
    }
}
