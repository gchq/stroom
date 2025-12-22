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

package stroom.pipeline.refdata;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.refdata.RefDataStoreHolder.MapAvailability;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class TestRefDataStoreHolder {

    @Mock
    private RefDataStoreFactory mockRefDataStoreFactory;
    @Mock
    private RefDataStore mockRefDataStore;

    private PipelineReference pipelineReference;

    @BeforeEach
    void setUp() {
        pipelineReference = new PipelineReference(
                null,
                DocRef.builder()
                        .type(FeedDoc.TYPE)
                        .uuid("MY_FEED")
                        .name("MY_FEED")
                        .build(),
                StreamTypeNames.REFERENCE);
    }

    @Test
    void testIsLookupNeeded_validMap() {

        Mockito.when(mockRefDataStore.getMapNames(Mockito.any()))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));


        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                "pipeUUID", "pipeVer", 1L);

        final String mapName = "foo";
        MapAvailability mapAvailability = refDataStoreHolder.getMapAvailabilityInStream(
                pipelineReference, refStreamDefinition, mapName);

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(mapAvailability)
                .isEqualTo(MapAvailability.UNKNOWN);
        Assertions.assertThat(mapAvailability.isLookupRequired())
                .isTrue();

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition);

        mapAvailability = refDataStoreHolder.getMapAvailabilityInStream(
                pipelineReference, refStreamDefinition, mapName);

        // The map is there so true
        Assertions.assertThat(mapAvailability)
                .isEqualTo(MapAvailability.PRESENT);
        Assertions.assertThat(mapAvailability.isLookupRequired())
                .isTrue();
    }

    @Test
    void testIsLookupNeeded_invalidMap() {

        Mockito.when(mockRefDataStore.getMapNames(Mockito.any()))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));

        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                "pipeUUID", "pipeVer", 1L);

        final String mapName = "UNKNOWN_MAP";

        MapAvailability mapAvailability = refDataStoreHolder.getMapAvailabilityInStream(
                pipelineReference, refStreamDefinition, mapName);

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(mapAvailability)
                .isEqualTo(MapAvailability.UNKNOWN);
        Assertions.assertThat(mapAvailability.isLookupRequired())
                .isTrue();

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition);

        mapAvailability = refDataStoreHolder.getMapAvailabilityInStream(
                pipelineReference, refStreamDefinition, mapName);

        // The map is there so true
        Assertions.assertThat(mapAvailability)
                .isEqualTo(MapAvailability.NOT_PRESENT);
        Assertions.assertThat(mapAvailability.isLookupRequired())
                .isFalse();
    }

    @Test
    void testIsLookupNeeded_multiple() {

        final RefStreamDefinition refStreamDefinition1 = new RefStreamDefinition(
                "pipeUUID1", "pipeVer1", 1L);
        final RefStreamDefinition refStreamDefinition2 = new RefStreamDefinition(
                "pipeUUID2", "pipeVer2", 2L);

        Mockito.when(mockRefDataStore.getMapNames(Mockito.eq(refStreamDefinition1)))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getMapNames(Mockito.eq(refStreamDefinition2)))
                .thenReturn(Set.of("bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));

        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);

        final String mapName = "foo";

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(refDataStoreHolder.getMapAvailabilityInStream(
                        pipelineReference, refStreamDefinition1, mapName))
                .isEqualTo(MapAvailability.UNKNOWN);
        Assertions.assertThat(refDataStoreHolder.getMapAvailabilityInStream(
                        pipelineReference, refStreamDefinition2, mapName))
                .isEqualTo(MapAvailability.UNKNOWN);

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition1);
        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition2);

        // The map is there
        Assertions.assertThat(refDataStoreHolder.getMapAvailabilityInStream(
                        pipelineReference, refStreamDefinition1, mapName))
                .isEqualTo(MapAvailability.PRESENT);
        // The map is not there
        Assertions.assertThat(refDataStoreHolder.getMapAvailabilityInStream(
                        pipelineReference, refStreamDefinition2, mapName))
                .isEqualTo(MapAvailability.NOT_PRESENT);
    }

    @TestFactory
    Stream<DynamicTest> testGetMapAvailability() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, Set<String>>>() {
                })
                .withOutputType(MapAvailability.class)
                .withTestFunction(testCase -> RefDataStoreHolder.getMapAvailability(
                        testCase.getInput()._1,
                        testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("CAT", Set.of("DOG", "CAT")), MapAvailability.PRESENT)
                .addCase(Tuple.of("HORSE", Set.of("DOG", "CAT")), MapAvailability.NOT_PRESENT)
                .addCase(Tuple.of("HORSE", Collections.emptySet()), MapAvailability.NOT_PRESENT)
                .addCase(Tuple.of("HORSE", null), MapAvailability.UNKNOWN)
                .build();
    }
}
