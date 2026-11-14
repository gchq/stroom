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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperties;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/// A build's indexing pipeline never writes into a production index: a Lucene index doc outside the build is stood
/// in for by the build's test index; a copy of a production Elasticsearch indexing pipeline is stepped, not
/// processed; promotion points a promoted pipeline back at the production index.
class TestBuildTestIndexes {

    private static final String BUILD = "acme-v2";
    private static final String PRODUCTION = "prod-index";
    private static final String TEST = "test-index";

    private final PipelineShapes shapes = Mockito.mock(PipelineShapes.class);
    private final PipelineDocs pipelineDocs = Mockito.mock(PipelineDocs.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final BuildRecords records = Mockito.mock(BuildRecords.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final IndexFields indexFields = Mockito.mock(IndexFields.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    private final BuildTestIndexes testIndexes = new BuildTestIndexes(shapes, pipelineDocs, guard, records,
            documentAccess, indexFields, keptMappings);

    TestBuildTestIndexes() {
        Mockito.when(guard.buildOf(any())).thenReturn(Optional.empty());
        Mockito.when(records.load(BUILD)).thenReturn(state);
        Mockito.when(records.entry(any(), any())).thenReturn(new LinkedHashMap<>());
        Mockito.when(shapes.merged(any())).thenReturn(merged);
    }

    @Test
    void onlyAnIndexingFilterNamingAnIndexOutsideTheBuildIsAProductionIndex() {
        final Map<String, String> elements = new LinkedHashMap<>();
        elements.put("xsltFilter", "XSLTFilter");
        elements.put("indexingFilter", "IndexingFilter");
        elements.put("ownFilter", "IndexingFilter");
        Mockito.when(merged.elements()).thenReturn(elements);
        Mockito.when(merged.properties()).thenReturn(Map.of(
                new ElementProperty("indexingFilter", "index"), doc(PRODUCTION),
                new ElementProperty("ownFilter", "index"), doc("own-index"),
                new ElementProperty("xsltFilter", "xslt"), Map.of("type", "XSLT", "uuid", "x")));
        Mockito.when(guard.buildOf(new DocRef(LuceneIndexDoc.TYPE, "own-index"))).thenReturn(Optional.of(BUILD));

        assertThat(testIndexes.productionIndexes(BUILD, merged)).containsExactly(Map.entry("indexingFilter",
                PRODUCTION));
    }

    @Test
    void aPipelineOutsideAnyBuildIsLeftAsItIs() {
        assertThat(testIndexes.redirect(new DocRef(PipelineDoc.TYPE, "p", "P"))).isEmpty();
        Mockito.verifyNoInteractions(pipelineDocs);
    }

    @Test
    void aCopyOfAProductionElasticsearchIndexingPipelineIsSteppedNotProcessed() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "p", "ACME - Indexing");
        Mockito.when(guard.buildOf(new DocRef(PipelineDoc.TYPE, "p"))).thenReturn(Optional.of(BUILD));
        Mockito.when(records.entry(BUILD, "p")).thenReturn(new LinkedHashMap<>(Map.of("copy_of", "original")));
        Mockito.when(merged.elements()).thenReturn(Map.of("elasticIndexingFilter", "ElasticIndexingFilter"));
        Mockito.when(merged.properties()).thenReturn(Map.of(new ElementProperty("elasticIndexingFilter",
                "indexName"), "acme-events"));
        Mockito.when(guard.folderContents(BUILD)).thenReturn(List.of());

        assertThatThrownBy(() -> testIndexes.redirect(pipeline)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("production index 'acme-events'")
                .hasMessageContaining("Step it clean")
                .hasMessageContaining("Nothing was processed");
    }

    @Test
    void anElasticsearchPipelineTheBuildMadeForItsOwnIndexProcesses() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "p", "ACME - Indexing");
        Mockito.when(guard.buildOf(new DocRef(PipelineDoc.TYPE, "p"))).thenReturn(Optional.of(BUILD));
        Mockito.when(merged.elements()).thenReturn(Map.of("elasticIndexingFilter", "ElasticIndexingFilter"));
        Mockito.when(merged.properties()).thenReturn(Map.of(new ElementProperty("elasticIndexingFilter",
                "indexName"), "acme-events"));

        assertThat(testIndexes.redirect(pipeline)).isEmpty();
        // A copy writing to an index of the build's own Elastic Index doc processes too.
        Mockito.when(records.entry(BUILD, "p")).thenReturn(new LinkedHashMap<>(Map.of("copy_of", "original")));
        final DocRef own = new DocRef(ElasticIndexDoc.TYPE, "e", "ACME index");
        Mockito.when(guard.folderContents(BUILD)).thenReturn(List.of(new WriteGuard.BuildDoc(ElasticIndexDoc.TYPE,
                "e", "ACME index", List.of(), null, null)));
        Mockito.when(documentAccess.read(own, ElasticIndexDoc.class)).thenReturn(Optional.of(ElasticIndexDoc.builder()
                .uuid("e").name("ACME index").indexName("acme-events").build()));
        assertThat(testIndexes.redirect(pipeline)).isEmpty();
    }

    @Test
    void theBuildsTestIndexesAreTheRecordedOnesAndSearchedForTheirProductionIndex() {
        state.put(BuildRecords.DOCS, Map.of(TEST, Map.of(BuildTestIndexes.TEST_INDEX_FOR, PRODUCTION),
                "other", Map.of("copy_of", "x")));
        Mockito.when(guard.folderContents(BUILD)).thenReturn(List.of(new WriteGuard.BuildDoc(LuceneIndexDoc.TYPE,
                TEST, "ACME-MCP-TEST", List.of(), null, null)));

        assertThat(testIndexes.testIndexes(BUILD)).containsExactly(Map.entry(TEST, PRODUCTION));
        assertThat(testIndexes.testIndexOf(BUILD, PRODUCTION)).map(DocRef::getUuid).contains(TEST);
        assertThat(testIndexes.searchedFor(BUILD, PRODUCTION)).map(DocRef::getUuid).contains(TEST);
        assertThat(testIndexes.searchedFor(BUILD, TEST)).map(DocRef::getUuid).contains(TEST);
        assertThat(testIndexes.searchedFor(BUILD, "another")).isEmpty();
        assertThat(testIndexes.testIndexOf(null, PRODUCTION)).isEmpty();
    }

    @Test
    void promotionPointsAPromotedPipelineBackAtTheProductionIndex() {
        final List<PipelineProperty> added = new ArrayList<>(List.of(
                new PipelineProperty("indexingFilter", "index", new PipelinePropertyValue(
                        new DocRef(LuceneIndexDoc.TYPE, TEST, "ACME-MCP-TEST"))),
                new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(new DocRef("XSLT", "x")))));
        final PipelineDoc doc = PipelineDoc.builder().uuid("p").name("P").pipelineData(new PipelineData(null,
                new PipelineProperties(added, List.of()), null, null)).build();
        Mockito.when(pipelineDocs.read("p")).thenReturn(doc);
        Mockito.when(documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, PRODUCTION), LuceneIndexDoc.class))
                .thenReturn(Optional.of(LuceneIndexDoc.builder().uuid(PRODUCTION).name("ACME").build()));

        assertThat(testIndexes.restoreIndex("p", Map.of(TEST, PRODUCTION))).isTrue();

        final ArgumentCaptor<PipelineDoc> written = ArgumentCaptor.forClass(PipelineDoc.class);
        Mockito.verify(pipelineDocs).write(written.capture());
        final List<PipelineProperty> now = PipelineDocs.addedProperties(written.getValue());
        assertThat(now).hasSize(2);
        assertThat(now.getFirst().getValue().getEntity()).isEqualTo(new DocRef(LuceneIndexDoc.TYPE, PRODUCTION,
                "ACME"));
        assertThat(now.getLast().getValue().getEntity().getUuid()).isEqualTo("x");
        // Nothing of the build's: unchanged.
        assertThat(testIndexes.restoreIndex("p", Map.of())).isFalse();
        assertThat(testIndexes.restoreIndex("p", Map.of("another", PRODUCTION))).isFalse();
    }

    @Test
    void promotionGivesTheProductionIndexThePlansFieldsItLacks() {
        // Seen in review: a working copy of an ordinary indexing pipeline, its plan adding a field, was written back
        // with the production index lacking it, and production indexing dropped the field's values.
        Mockito.when(merged.elements()).thenReturn(Map.of("indexingFilter", "IndexingFilter"));
        Mockito.when(merged.properties()).thenReturn(Map.of(new ElementProperty("indexingFilter", "index"),
                doc(TEST)));
        state.put(BuildRecords.DOCS, Map.of(TEST, Map.of(BuildTestIndexes.TEST_INDEX_FOR, PRODUCTION)));
        Mockito.when(guard.buildOf(new DocRef(LuceneIndexDoc.TYPE, TEST))).thenReturn(Optional.of(BUILD));
        Mockito.when(keptMappings.keptMapping("p")).thenReturn(Optional.of(new KeptMapping.Kept("index", Map.of(
                "backend", "lucene", "index_name", "ACME", "time_field", "EventTime", "fields", List.of(
                        Map.of("name", "EventTime", "type", "date", "source", "EventTime/TimeCreated"),
                        Map.of("name", "NewField", "type", "keyword", "source", "EventSource/Generator"))))));
        Mockito.when(documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, PRODUCTION), LuceneIndexDoc.class))
                .thenReturn(Optional.of(LuceneIndexDoc.builder().uuid(PRODUCTION).name("ACME").build()));
        Mockito.when(indexFields.indexFields(any())).thenReturn(List.of(Map.of("name", "EventTime")));
        final List<Map<String, Object>> docs = List.of(Map.of("type", PipelineDoc.TYPE, "uuid", "p"),
                Map.of("type", "XSLT", "uuid", "x"));

        final List<BuildTestIndexes.FieldAddition> additions = testIndexes.promotionAdditions(BUILD, docs,
                Set.of());

        assertThat(additions).singleElement().satisfies(addition -> {
            assertThat(addition.indexUuid()).isEqualTo(PRODUCTION);
            assertThat(addition.fields()).containsExactly("NewField");
            assertThat(addition.said()).isEqualTo("index ACME: adds NewField (forward only: documents indexed "
                                                  + "before don't have them)");
        });
        // An aggregated feed's group index is given its fields otherwise.
        assertThat(testIndexes.promotionAdditions(BUILD, docs, Set.of(PRODUCTION))).isEmpty();
        // Every field there already: nothing.
        Mockito.when(indexFields.indexFields(any())).thenReturn(List.of(Map.of("name", "EventTime"),
                Map.of("name", "NewField")));
        assertThat(testIndexes.promotionAdditions(BUILD, docs, Set.of())).isEmpty();
    }

    @Test
    void aPipelineWritingToTheBuildsOwnIndexAddsNothingAtPromotion() {
        Mockito.when(merged.elements()).thenReturn(Map.of("indexingFilter", "IndexingFilter"));
        Mockito.when(merged.properties()).thenReturn(Map.of(new ElementProperty("indexingFilter", "index"),
                doc("own-index")));
        Mockito.when(guard.buildOf(new DocRef(LuceneIndexDoc.TYPE, "own-index"))).thenReturn(Optional.of(BUILD));
        Mockito.when(keptMappings.keptMapping("p")).thenReturn(Optional.of(new KeptMapping.Kept("index", Map.of(
                "backend", "lucene", "index_name", "ACME", "time_field", "EventTime", "fields", List.of(
                        Map.of("name", "EventTime", "type", "date", "source", "EventTime/TimeCreated"))))));

        assertThat(testIndexes.promotionAdditions(BUILD, List.of(Map.of("type", PipelineDoc.TYPE, "uuid", "p")),
                Set.of())).isEmpty();
        Mockito.verifyNoInteractions(indexFields);
    }

    private static Map<String, Object> doc(final String uuid) {
        return Map.of("type", LuceneIndexDoc.TYPE, "uuid", uuid, "name", uuid);
    }
}
