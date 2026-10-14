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

package stroom.sqlstore.impl.pipeline;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.StringValue;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.UpdatableTemporalStoreProvider;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TemporalEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSqlStoreLookup {

    @Mock
    private UpdatableTemporalStoreProvider storeProvider;

    @Mock
    private UpdatableTemporalStore updatableTemporalStore;

    @Mock
    private LookupIdentifier lookupIdentifier;

    @Mock
    private ReferenceDataResult referenceDataResult;

    @Test
    void testLookupPlainValue() {
        Mockito.when(lookupIdentifier.getPrimaryMapName()).thenReturn("my-map");
        Mockito.when(lookupIdentifier.getKey()).thenReturn("k1");
        Mockito.when(lookupIdentifier.getEventTime()).thenReturn(1000L);

        Mockito.when(storeProvider.get("my-map")).thenReturn(updatableTemporalStore);

        final TemporalEntry entry = new TemporalEntry("my-map", "k1", 1000L, "my-plain-val");
        final ResultPage<TemporalEntry> resultPage = ResultPage.createPageLimitedList(List.of(entry), null);

        // The lookup resolves the store once then queries by DocRef, so both are stubbed.
        Mockito.when(updatableTemporalStore.resolveStoreByName("my-map"))
                .thenReturn(new DocRef(SqlTemporalStoreDoc.TYPE, "store-uuid", "my-map"));
        Mockito.when(updatableTemporalStore.find(
                        Mockito.any(DocRef.class), Mockito.any(ExpressionCriteria.class)))
                .thenReturn(resultPage);

        final SqlStoreLookupImpl sqlStoreLookup = new SqlStoreLookupImpl(storeProvider);
        sqlStoreLookup.lookup(lookupIdentifier, referenceDataResult);

        final ArgumentCaptor<RefDataValueProxy> proxyCaptor = ArgumentCaptor.forClass(RefDataValueProxy.class);
        Mockito.verify(referenceDataResult, Mockito.times(1)).addRefDataValueProxy(proxyCaptor.capture());

        final RefDataValueProxy capturedProxy = proxyCaptor.getValue();
        assertThat(capturedProxy).isNotNull();
        assertThat(capturedProxy.getKey()).isEqualTo("k1");
        assertThat(capturedProxy.getMapName()).isEqualTo("my-map");

        final Optional<RefDataValue> optVal = capturedProxy.supplyValue();
        assertThat(optVal).isPresent();
        assertThat(optVal.get()).isInstanceOf(StringValue.class);
        assertThat(((StringValue) optVal.get()).getValue()).isEqualTo("my-plain-val");
    }

    @Test
    void testLookupXmlValue() {
        Mockito.when(lookupIdentifier.getPrimaryMapName()).thenReturn("my-map");
        Mockito.when(lookupIdentifier.getKey()).thenReturn("k1");
        Mockito.when(lookupIdentifier.getEventTime()).thenReturn(1000L);

        Mockito.when(storeProvider.get("my-map")).thenReturn(updatableTemporalStore);

        final TemporalEntry entry = new TemporalEntry(
                "my-map", "k1", 1000L, "<Location><Country>UK</Country></Location>");
        final ResultPage<TemporalEntry> resultPage = ResultPage.createPageLimitedList(List.of(entry), null);

        // The lookup resolves the store once then queries by DocRef, so both are stubbed.
        Mockito.when(updatableTemporalStore.resolveStoreByName("my-map"))
                .thenReturn(new DocRef(SqlTemporalStoreDoc.TYPE, "store-uuid", "my-map"));
        Mockito.when(updatableTemporalStore.find(
                        Mockito.any(DocRef.class), Mockito.any(ExpressionCriteria.class)))
                .thenReturn(resultPage);

        final SqlStoreLookupImpl sqlStoreLookup = new SqlStoreLookupImpl(storeProvider);
        sqlStoreLookup.lookup(lookupIdentifier, referenceDataResult);

        final ArgumentCaptor<RefDataValueProxy> proxyCaptor = ArgumentCaptor.forClass(RefDataValueProxy.class);
        Mockito.verify(referenceDataResult, Mockito.times(1)).addRefDataValueProxy(proxyCaptor.capture());

        final RefDataValueProxy capturedProxy = proxyCaptor.getValue();
        assertThat(capturedProxy).isNotNull();

        final Optional<RefDataValue> optVal = capturedProxy.supplyValue();
        assertThat(optVal).isPresent();
        assertThat(optVal.get()).isInstanceOf(FastInfosetValue.class);
    }

    /**
     * The store name is resolved once per pipeline instance, not once per lookup.
     *
     * <p>Resolving lists the document store - a database query with no cache behind it - and
     * permission-checks every row. An XSLT {@code lookup()} runs once per event, so without this
     * a million-event stream issued a million of those listings.</p>
     */
    @Test
    void testStoreNameIsResolvedOncePerPipelineInstance() {
        Mockito.when(lookupIdentifier.getPrimaryMapName()).thenReturn("my-map");
        Mockito.when(lookupIdentifier.getKey()).thenReturn("k1");
        Mockito.when(lookupIdentifier.getEventTime()).thenReturn(1000L);
        Mockito.when(storeProvider.get("my-map")).thenReturn(updatableTemporalStore);
        Mockito.when(updatableTemporalStore.resolveStoreByName("my-map"))
                .thenReturn(new DocRef(SqlTemporalStoreDoc.TYPE, "store-uuid", "my-map"));
        Mockito.when(updatableTemporalStore.find(
                        Mockito.any(DocRef.class), Mockito.any(ExpressionCriteria.class)))
                .thenReturn(ResultPage.createUnboundedList(new ArrayList<>()));

        final SqlStoreLookupImpl lookup = new SqlStoreLookupImpl(storeProvider);
        for (int i = 0; i < 25; i++) {
            lookup.lookup(lookupIdentifier, referenceDataResult);
        }

        // Resolved once...
        Mockito.verify(updatableTemporalStore, Mockito.times(1)).resolveStoreByName("my-map");
        // ...but every lookup still runs its own query, and permission is still checked per call
        // inside find - a cached DocRef is a cached identity, not cached authorisation.
        Mockito.verify(updatableTemporalStore, Mockito.times(25))
                .find(Mockito.any(DocRef.class), Mockito.any(ExpressionCriteria.class));
    }
}
