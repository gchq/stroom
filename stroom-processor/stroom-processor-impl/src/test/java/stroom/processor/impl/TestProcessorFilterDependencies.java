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

package stroom.processor.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProcessorFilterDependencies {

    private static final int FILTER_ID = 42;
    private static final int FILTER_VERSION = 7;
    private static final String FILTER_UUID = "filter-uuid";
    private static final String PIPELINE_UUID = "pipeline-uuid";
    private static final String PIPELINE_NAME = "My Pipeline";
    private static final DocRef FILTER_DOC_REF = new DocRef(ProcessorFilter.ENTITY_TYPE, FILTER_UUID);
    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, PIPELINE_UUID, PIPELINE_NAME);
    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "My Index");
    private static final DocRef FEED = new DocRef("Feed", "feed-uuid", "MY_FEED");

    @Mock
    private ProcessorFilterDao processorFilterDao;
    @Mock
    private DocFinder docFinder;
    @Mock
    private DocDependencyService docDependencyService;

    private ProcessorFilterDependencies processorFilterDependencies;

    @BeforeEach
    void setUp() {
        lenient().when(docFinder.getName(any())).thenReturn(Optional.of(PIPELINE_NAME));
        processorFilterDependencies = new ProcessorFilterDependencies(
                processorFilterDao,
                docFinder,
                new MockSecurityContext(),
                () -> docDependencyService);
    }

    static Stream<DocRef> streamStoreDataSources() {
        return Stream.of(
                // The current stream store ref
                MetaFields.STREAM_STORE_DOC_REF,
                // Legacy refs still held by older filters
                DocRef.builder().type("StreamStore").uuid("0").build(),
                DocRef.builder().type("Searchable").uuid("Meta Store").build());
    }

    @ParameterizedTest
    @MethodSource("streamStoreDataSources")
    void testGetDependencies_streamStoreDataSourceExcluded(final DocRef dataSource) {
        // Regression for gh-5979: the stream store is not a document, so a dependency on it was always
        // reported as missing, and legacy refs to it were not recognised at all.
        final ProcessorFilter filter = filter(dataSource, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .containsExactly(PIPELINE);
    }

    @Test
    void testGetDependencies_nullDataSource() {
        final ProcessorFilter filter = filter(null, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .containsExactly(PIPELINE);
    }

    @Test
    void testGetDependencies_indexDataSourceKept() {
        // A filter created from a dashboard search reads from the dashboard's data source
        final ProcessorFilter filter = filter(INDEX, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .containsExactlyInAnyOrder(PIPELINE, INDEX);
    }

    @Test
    void testGetDependencies_expressionDocRefs() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, FEED)
                .build();
        final ProcessorFilter filter = filter(MetaFields.STREAM_STORE_DOC_REF, expression);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .containsExactlyInAnyOrder(PIPELINE, FEED);
    }

    @Test
    void testGetDependencies_pipelineNameResolved() {
        final ProcessorFilter filter = filter(null, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .singleElement()
                .extracting(DocRef::getName)
                .isEqualTo(PIPELINE_NAME);
    }

    @Test
    void testGetDependencies_pipelineNotFound() {
        when(docFinder.getName(any())).thenReturn(Optional.empty());
        final ProcessorFilter filter = filter(null, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .singleElement()
                .extracting(DocRef::getName)
                .isEqualTo("Unknown");
    }

    @Test
    void testGetDependencies_streamingAnalytic() {
        // The processor of a streaming analytic filter holds an analytic rule rather than a pipeline
        final DocRef analyticRule = new DocRef(AnalyticRuleDoc.TYPE, PIPELINE_UUID, "My Rule");
        when(docFinder.getName(analyticRule)).thenReturn(Optional.of("My Rule"));
        final ProcessorFilter filter = ProcessorFilter.builder()
                .uuid(FILTER_UUID)
                .processor(Processor.builder()
                        .processorType(ProcessorType.STREAMING_ANALYTIC)
                        .pipelineUuid(PIPELINE_UUID)
                        .build())
                .queryData(QueryData.builder().build())
                .build();

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .singleElement()
                .satisfies(dependency -> {
                    assertThat(dependency.getType())
                            .isEqualTo(AnalyticRuleDoc.TYPE);
                    assertThat(dependency.getName())
                            .isEqualTo("My Rule");
                });
        verify(docFinder).getName(argThat(docRef -> AnalyticRuleDoc.TYPE.equals(docRef.getType())));
    }

    @Test
    void testGetDependencies_pipelineType() {
        final ProcessorFilter filter = filter(null, null);

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .singleElement()
                .extracting(DocRef::getType)
                .isEqualTo(PipelineDoc.TYPE);
    }

    @Test
    void testGetDependencies_noPipeline() {
        final ProcessorFilter filter = ProcessorFilter.builder()
                .uuid(FILTER_UUID)
                .processor(Processor.builder().build())
                .queryData(QueryData.builder().dataSource(INDEX).build())
                .build();

        final Set<DocRef> dependencies = processorFilterDependencies.getDependencies(filter);

        assertThat(dependencies)
                .containsExactly(INDEX);
    }

    @Test
    void testUpdate_writesEdgesAndStamps() {
        when(processorFilterDao.setDependencyVersion(anyInt(), anyInt(), anyInt())).thenReturn(true);

        final boolean stamped = processorFilterDependencies.update(filter(INDEX, null));

        assertThat(stamped)
                .isTrue();
        verify(docDependencyService).setDependencies(FILTER_DOC_REF, Set.of(PIPELINE, INDEX));
        verify(processorFilterDao).setDependencyVersion(
                FILTER_ID, FILTER_VERSION, ProcessorFilterDependencies.CURRENT_DEPENDENCY_VERSION);
    }

    @Test
    void testUpdate_filterChangedSinceRead() {
        when(processorFilterDao.setDependencyVersion(anyInt(), anyInt(), anyInt())).thenReturn(false);

        final boolean stamped = processorFilterDependencies.update(filter(null, null));

        assertThat(stamped)
                .isFalse();
    }

    @Test
    void testUpdate_noUuid() {
        final ProcessorFilter filter = filter(null, null).copy().uuid(null).build();

        final boolean stamped = processorFilterDependencies.update(filter);

        assertThat(stamped)
                .isFalse();
        verifyNoInteractions(docDependencyService, processorFilterDao);
    }

    @Test
    void testUpdate_noIdOrVersion() {
        final ProcessorFilter filter = filter(null, null).copy().id(null).version(null).build();

        final boolean stamped = processorFilterDependencies.update(filter);

        assertThat(stamped)
                .isFalse();
        verify(docDependencyService).setDependencies(FILTER_DOC_REF, Set.of(PIPELINE));
        verify(processorFilterDao, never()).setDependencyVersion(anyInt(), anyInt(), anyInt());
    }

    @Test
    void testRemove() {
        processorFilterDependencies.remove(FILTER_UUID);

        verify(docDependencyService).removeDependencies(FILTER_DOC_REF);
    }

    @Test
    void testRemove_nullUuid() {
        processorFilterDependencies.remove((String) null);

        verifyNoInteractions(docDependencyService);
    }

    @Test
    void testRemove_multiple() {
        processorFilterDependencies.remove(List.of("uuid-1", "uuid-2"));

        verify(docDependencyService).removeDependencies(new DocRef(ProcessorFilter.ENTITY_TYPE, "uuid-1"));
        verify(docDependencyService).removeDependencies(new DocRef(ProcessorFilter.ENTITY_TYPE, "uuid-2"));
    }

    @Test
    void testRemoveForDeleted() {
        when(processorFilterDao.setDependencyVersion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        final ProcessorFilter filter = filter(INDEX, null).copy().deleted(true).build();

        final boolean stamped = processorFilterDependencies.removeForDeleted(filter);

        assertThat(stamped)
                .isTrue();
        verify(docDependencyService).removeDependencies(FILTER_DOC_REF);
        verify(docDependencyService, never()).setDependencies(any(), any());
        verify(processorFilterDao).setDependencyVersion(
                FILTER_ID, FILTER_VERSION, ProcessorFilterDependencies.CURRENT_DEPENDENCY_VERSION);
    }

    private static ProcessorFilter filter(final DocRef dataSource, final ExpressionOperator expression) {
        return ProcessorFilter.builder()
                .id(FILTER_ID)
                .version(FILTER_VERSION)
                .uuid(FILTER_UUID)
                .processor(Processor.builder().pipelineUuid(PIPELINE_UUID).build())
                .queryData(QueryData.builder()
                        .dataSource(dataSource)
                        .expression(expression)
                        .build())
                .build();
    }
}
