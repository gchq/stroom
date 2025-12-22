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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStreamTypeExtensions {

    @Test
    void testGetExtension() {
        final StreamTypeExtensions streamTypeExtensions = new StreamTypeExtensions(
                FsVolumeConfig::new);

        Assertions.assertThat(streamTypeExtensions.getExtension(InternalStreamTypeNames.BOUNDARY_INDEX))
                .isEqualTo("bdy");

        Assertions.assertThat(streamTypeExtensions.getExtension(StreamTypeNames.EVENTS))
                .isEqualTo("evt");

        Assertions.assertThat(streamTypeExtensions.getExtension("FOO"))
                .isEqualTo("dat");
    }

    @Test
    void testGetType() {
        final StreamTypeExtensions streamTypeExtensions = new StreamTypeExtensions(
                FsVolumeConfig::new);

        Assertions.assertThat(streamTypeExtensions.getChildType("ctx"))
                .isEqualTo(StreamTypeNames.CONTEXT);

        Assertions.assertThat(streamTypeExtensions.getChildType("mf"))
                .isEqualTo(InternalStreamTypeNames.MANIFEST);

        Assertions.assertThat(streamTypeExtensions.getChildType("meta"))
                .isEqualTo(StreamTypeNames.META);

        Assertions.assertThat(streamTypeExtensions.getChildType("foo"))
                .isNull();
    }
}
