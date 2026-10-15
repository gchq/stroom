/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.state;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.meta.shared.Meta;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaHolder {

    @Test
    void testInitialState() {
        final MetaHolder metaHolder = new MetaHolder();

        assertThat(metaHolder.getMeta())
                .isNull();
        assertThat(metaHolder.getMetaId())
                .isNull();
        assertThat(metaHolder.getChildDataType())
                .isNull();
        assertThat(metaHolder.getInputStreamProvider())
                .isNull();
        assertThat(metaHolder.getPartIndex())
                .isZero();
        assertThat(metaHolder.getPartNo())
                .isEqualTo(1L);
    }

    @Test
    void testSetAndGetMeta() {
        final MetaHolder metaHolder = new MetaHolder();
        final Meta meta = Meta.builder()
                .id(123L)
                .build();

        metaHolder.setMeta(meta);

        assertThat(metaHolder.getMeta())
                .isEqualTo(meta);
        assertThat(metaHolder.getMetaId())
                .isEqualTo(123L);

        metaHolder.setMeta(null);

        assertThat(metaHolder.getMeta())
                .isNull();
        assertThat(metaHolder.getMetaId())
                .isNull();
    }

    @Test
    void testSetAndGetChildDataType() {
        final MetaHolder metaHolder = new MetaHolder();

        metaHolder.setChildDataType(StreamTypeNames.CONTEXT);
        assertThat(metaHolder.getChildDataType())
                .isEqualTo(StreamTypeNames.CONTEXT);

        // Setting META should be ignored and keep the existing child data type
        metaHolder.setChildDataType(StreamTypeNames.META);
        assertThat(metaHolder.getChildDataType())
                .isEqualTo(StreamTypeNames.CONTEXT);

        // Setting a different valid child data type
        metaHolder.setChildDataType(StreamTypeNames.EVENTS);
        assertThat(metaHolder.getChildDataType())
                .isEqualTo(StreamTypeNames.EVENTS);

        // Setting null
        metaHolder.setChildDataType(null);
        assertThat(metaHolder.getChildDataType())
                .isNull();

        // Setting META when null should still be ignored
        metaHolder.setChildDataType(StreamTypeNames.META);
        assertThat(metaHolder.getChildDataType())
                .isNull();
    }

    @Test
    void testSetAndGetInputStreamProvider() {
        final MetaHolder metaHolder = new MetaHolder();
        final InputStreamProvider inputStreamProvider = Mockito.mock(InputStreamProvider.class);

        metaHolder.setInputStreamProvider(inputStreamProvider);

        assertThat(metaHolder.getInputStreamProvider())
                .isEqualTo(inputStreamProvider);

        metaHolder.setInputStreamProvider(null);

        assertThat(metaHolder.getInputStreamProvider())
                .isNull();
    }

    @Test
    void testSetAndGetPartIndex() {
        final MetaHolder metaHolder = new MetaHolder();

        metaHolder.setPartIndex(0L);
        assertThat(metaHolder.getPartIndex())
                .isZero();
        assertThat(metaHolder.getPartNo())
                .isEqualTo(1L);

        metaHolder.setPartIndex(5L);
        assertThat(metaHolder.getPartIndex())
                .isEqualTo(5L);
        assertThat(metaHolder.getPartNo())
                .isEqualTo(6L);

        metaHolder.setPartIndex(99L);
        assertThat(metaHolder.getPartIndex())
                .isEqualTo(99L);
        assertThat(metaHolder.getPartNo())
                .isEqualTo(100L);
    }
}
