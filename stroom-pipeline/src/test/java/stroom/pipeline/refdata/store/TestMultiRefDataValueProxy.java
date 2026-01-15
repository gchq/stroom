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

package stroom.pipeline.refdata.store;

import jakarta.validation.constraints.NotNull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class TestMultiRefDataValueProxy {

    private static final String A_KEY = "aKey";
    private static final String OTHER_KEY = "otherKey";

    private static final String A_MAP = "aMap";
    private static final String OTHER_MAP = "otherMap";

    @Test
    void testMerge_twoSingles() {

        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(1L);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(2L);

        createMultiRefDataValueProxy(singleRefDataValueProxy1, singleRefDataValueProxy2);
    }

    private RefDataValueProxy createMultiRefDataValueProxy(
            final SingleRefDataValueProxy singleRefDataValueProxy1,
            final SingleRefDataValueProxy singleRefDataValueProxy2) {

        Assertions.assertThat(singleRefDataValueProxy1.getMapDefinitions())
                .hasSize(1);

        Assertions.assertThat(singleRefDataValueProxy2.getMapDefinitions())
                .hasSize(1);

        final MultiRefDataValueProxy multiRefDataValueProxy = MultiRefDataValueProxy.merge(
                singleRefDataValueProxy1,
                singleRefDataValueProxy2);

        Assertions.assertThat(multiRefDataValueProxy.getKey())
                .isEqualTo(A_KEY);
        Assertions.assertThat(multiRefDataValueProxy.getMapName())
                .isEqualTo(A_MAP);
        Assertions.assertThat(multiRefDataValueProxy.getMapDefinitions())
                .containsExactly(
                        singleRefDataValueProxy1.getMapDefinitions().get(0),
                        singleRefDataValueProxy2.getMapDefinitions().get(0));

        return multiRefDataValueProxy;
    }

    @Test
    void testMerge_oneSingleOneMulti() {

        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(1L);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(2L);

        final RefDataValueProxy multiRefDataValueProxy1 = createMultiRefDataValueProxy(
                singleRefDataValueProxy1,
                singleRefDataValueProxy2);

        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(3L);

        final RefDataValueProxy multiRefDataValueProxy2a = MultiRefDataValueProxy.merge(
                multiRefDataValueProxy1,
                singleRefDataValueProxy3);

        final RefDataValueProxy multiRefDataValueProxy2b = multiRefDataValueProxy1.merge(
                singleRefDataValueProxy3);

        Assertions.assertThat(multiRefDataValueProxy2a.getKey())
                .isEqualTo(A_KEY);
        Assertions.assertThat(multiRefDataValueProxy2a.getMapName())
                .isEqualTo(A_MAP);
        Assertions.assertThat(multiRefDataValueProxy2a.getMapDefinitions())
                .containsExactly(
                        singleRefDataValueProxy1.getMapDefinitions().get(0),
                        singleRefDataValueProxy2.getMapDefinitions().get(0),
                        singleRefDataValueProxy3.getMapDefinitions().get(0));

        Assertions.assertThat(multiRefDataValueProxy2a)
                .isEqualTo(multiRefDataValueProxy2b);

        // Now do the merge the other way round, as order is important
        final RefDataValueProxy multiRefDataValueProxy2c = singleRefDataValueProxy3.merge(
                multiRefDataValueProxy1);

        Assertions.assertThat(multiRefDataValueProxy2c.getMapDefinitions())
                .containsExactly(
                        singleRefDataValueProxy3.getMapDefinitions().get(0),
                        singleRefDataValueProxy1.getMapDefinitions().get(0),
                        singleRefDataValueProxy2.getMapDefinitions().get(0));

        Assertions.assertThat(multiRefDataValueProxy2a)
                .isNotEqualTo(multiRefDataValueProxy2c);
    }

    @Test
    void testMerge_towMulti() {

        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(1L);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(2L);
        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(3L);
        final SingleRefDataValueProxy singleRefDataValueProxy4 = createSingleRefDataValueProxy(4L);

        final RefDataValueProxy multiRefDataValueProxy1 = createMultiRefDataValueProxy(
                singleRefDataValueProxy1,
                singleRefDataValueProxy2);

        final RefDataValueProxy multiRefDataValueProxy2 = createMultiRefDataValueProxy(
                singleRefDataValueProxy3,
                singleRefDataValueProxy4);

        final RefDataValueProxy multiRefDataValueProxy3a = MultiRefDataValueProxy.merge(
                multiRefDataValueProxy1,
                multiRefDataValueProxy2);

        final RefDataValueProxy multiRefDataValueProxy3b = multiRefDataValueProxy1.merge(
                multiRefDataValueProxy2);

        Assertions.assertThat(multiRefDataValueProxy3a.getKey())
                .isEqualTo(A_KEY);
        Assertions.assertThat(multiRefDataValueProxy3a.getMapName())
                .isEqualTo(A_MAP);
        Assertions.assertThat(multiRefDataValueProxy3a.getMapDefinitions())
                .containsExactly(
                        singleRefDataValueProxy1.getMapDefinitions().get(0),
                        singleRefDataValueProxy2.getMapDefinitions().get(0),
                        singleRefDataValueProxy3.getMapDefinitions().get(0),
                        singleRefDataValueProxy4.getMapDefinitions().get(0));

        Assertions.assertThat(multiRefDataValueProxy3a)
                .isEqualTo(multiRefDataValueProxy3b);

        // Now do the merge the other way round, as order is important
        final RefDataValueProxy multiRefDataValueProxy3c = multiRefDataValueProxy2.merge(
                multiRefDataValueProxy1);

        Assertions.assertThat(multiRefDataValueProxy3c.getMapDefinitions())
                .containsExactly(
                        singleRefDataValueProxy3.getMapDefinitions().get(0),
                        singleRefDataValueProxy4.getMapDefinitions().get(0),
                        singleRefDataValueProxy1.getMapDefinitions().get(0),
                        singleRefDataValueProxy2.getMapDefinitions().get(0));

        Assertions.assertThat(multiRefDataValueProxy3a)
                .isNotEqualTo(multiRefDataValueProxy3c);
    }

    @Test
    void testBadKey() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, true);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                OTHER_KEY, A_MAP, 2L, true);

        Assertions.assertThatThrownBy(() ->
                        MultiRefDataValueProxy.merge(singleRefDataValueProxy1, singleRefDataValueProxy2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("key");
    }

    @Test
    void testBadMap() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, true);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                A_KEY, OTHER_MAP, 2L, true);

        Assertions.assertThatThrownBy(() ->
                        MultiRefDataValueProxy.merge(singleRefDataValueProxy1, singleRefDataValueProxy2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("map");
    }

    @Test
    void testSupplyValue1() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, true);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 2L, true);
        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 3L, true);

        final RefDataValueProxy multiRefDataValueProxy = singleRefDataValueProxy1
                .merge(singleRefDataValueProxy2)
                .merge(singleRefDataValueProxy3);

        final Optional<RefDataValue> optRefDataValue = multiRefDataValueProxy.supplyValue();

        Assertions.assertThat(optRefDataValue)
                .isPresent()
                .hasValue(createVal(A_KEY, A_MAP, 1L)); // 1 is first one with a val
    }

    @Test
    void testSupplyValue2() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, false);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 2L, true);
        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 3L, true);

        final RefDataValueProxy multiRefDataValueProxy = singleRefDataValueProxy1
                .merge(singleRefDataValueProxy2)
                .merge(singleRefDataValueProxy3);

        final Optional<RefDataValue> optRefDataValue = multiRefDataValueProxy.supplyValue();

        Assertions.assertThat(optRefDataValue)
                .isPresent()
                .hasValue(createVal(A_KEY, A_MAP, 2L)); // 2 is first one with a val
    }

    @Test
    void testSupplyValue3() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, false);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 2L, false);
        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 3L, true);

        final RefDataValueProxy multiRefDataValueProxy = singleRefDataValueProxy1
                .merge(singleRefDataValueProxy2)
                .merge(singleRefDataValueProxy3);

        final Optional<RefDataValue> optRefDataValue = multiRefDataValueProxy.supplyValue();

        Assertions.assertThat(optRefDataValue)
                .isPresent()
                .hasValue(createVal(A_KEY, A_MAP, 3L)); // 3 is first one with a val
    }

    @Test
    void testSupplyValue_noValue() {
        final SingleRefDataValueProxy singleRefDataValueProxy1 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 1L, false);
        final SingleRefDataValueProxy singleRefDataValueProxy2 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 2L, false);
        final SingleRefDataValueProxy singleRefDataValueProxy3 = createSingleRefDataValueProxy(
                A_KEY, A_MAP, 3L, false);

        final RefDataValueProxy multiRefDataValueProxy = singleRefDataValueProxy1
                .merge(singleRefDataValueProxy2)
                .merge(singleRefDataValueProxy3);

        final Optional<RefDataValue> optRefDataValue = multiRefDataValueProxy.supplyValue();

        Assertions.assertThat(optRefDataValue)
                .isEmpty();
    }

    private SingleRefDataValueProxy createSingleRefDataValueProxy(final long streamId) {
        return createSingleRefDataValueProxy(A_KEY, A_MAP, streamId, true);
    }

    private SingleRefDataValueProxy createSingleRefDataValueProxy(final String key,
                                                                  final String map,
                                                                  final long streamId,
                                                                  final boolean hasValue) {
        final RefDataStore mockRefDataStore = Mockito.mock(RefDataStore.class);
        final RefDataValue refDataValue = hasValue
                ? createVal(key, map, streamId)
                : null;

        Mockito.when(mockRefDataStore.getValue(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.ofNullable(refDataValue));

        return new SingleRefDataValueProxy(
                mockRefDataStore,
                new MapDefinition(
                        new RefStreamDefinition("pipeUuid", "pipeVer", streamId),
                        map),
                key);
    }

    @NotNull
    private StringValue createVal(final String key, final String map, final long streamId) {
        return new StringValue(String.join(":", key, map, Long.toString(streamId)));
    }
}
