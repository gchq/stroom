package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class TestRefDataStoreHolder {

    @Mock
    RefDataStoreFactory mockRefDataStoreFactory;
    @Mock
    RefDataStore mockRefDataStore;

    @Test
    void testIsLookupNeeded_validMap() {

        Mockito.when(mockRefDataStoreFactory.getOffHeapStore())
                .thenReturn(mockRefDataStore);
        Mockito.when(mockRefDataStore.getMapNames(Mockito.any()))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));

        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                "pipeUUID", "pipeVer", 1L);

        final String mapName = "foo";
        boolean isLookupNeeded = refDataStoreHolder.isLookupNeeded(refStreamDefinition, mapName);

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(isLookupNeeded)
                .isTrue();

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition);

        isLookupNeeded = refDataStoreHolder.isLookupNeeded(refStreamDefinition, mapName);

        // The map is there so true
        Assertions.assertThat(isLookupNeeded)
                .isTrue();
    }

    @Test
    void testIsLookupNeeded_invalidMap() {

        Mockito.when(mockRefDataStoreFactory.getOffHeapStore())
                .thenReturn(mockRefDataStore);
        Mockito.when(mockRefDataStore.getMapNames(Mockito.any()))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));

        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                "pipeUUID", "pipeVer", 1L);

        final String mapName = "UNKNOWN_MAP";

        boolean isLookupNeeded = refDataStoreHolder.isLookupNeeded(refStreamDefinition, mapName);

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(isLookupNeeded)
                .isTrue();

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition);

        isLookupNeeded = refDataStoreHolder.isLookupNeeded(refStreamDefinition, mapName);

        // The map is there so true
        Assertions.assertThat(isLookupNeeded)
                .isFalse();
    }

    @Test
    void testIsLookupNeeded_multiple() {

        final RefStreamDefinition refStreamDefinition1 = new RefStreamDefinition(
                "pipeUUID1", "pipeVer1", 1L);
        final RefStreamDefinition refStreamDefinition2 = new RefStreamDefinition(
                "pipeUUID2", "pipeVer2", 2L);

        Mockito.when(mockRefDataStoreFactory.getOffHeapStore())
                .thenReturn(mockRefDataStore);
        Mockito.when(mockRefDataStore.getMapNames(Mockito.eq(refStreamDefinition1)))
                .thenReturn(Set.of("foo", "bar"));
        Mockito.when(mockRefDataStore.getMapNames(Mockito.eq(refStreamDefinition2)))
                .thenReturn(Set.of("bar"));
        Mockito.when(mockRefDataStore.getLoadState(Mockito.any()))
                .thenReturn(Optional.of(ProcessingState.COMPLETE));

        final RefDataStoreHolder refDataStoreHolder = new RefDataStoreHolder(mockRefDataStoreFactory);

        final String mapName = "foo";

        // Always true at this point as it doesn't know either way
        Assertions.assertThat(refDataStoreHolder.isLookupNeeded(refStreamDefinition1, mapName))
                .isTrue();
        Assertions.assertThat(refDataStoreHolder.isLookupNeeded(refStreamDefinition2, mapName))
                .isTrue();

        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition1);
        refDataStoreHolder.addKnownMapNames(mockRefDataStore, refStreamDefinition2);

        // The map is there
        Assertions.assertThat(refDataStoreHolder.isLookupNeeded(refStreamDefinition1, mapName))
                .isTrue();
        // The map is not there
        Assertions.assertThat(refDataStoreHolder.isLookupNeeded(refStreamDefinition2, mapName))
                .isFalse();
    }
}
