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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestAnalyticRuleStoreImpl {

    private static final DocRef RULE_DOC_REF = AnalyticRuleDoc.buildDocRef()
            .uuid("8d3d3a07-7c4e-4b8e-9d55-2f0f6a3b1c21")
            .name("Test rule")
            .build();

    @Mock
    private StoreFactory mockStoreFactory;
    @Mock
    private Store<AnalyticRuleDoc> mockStore;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private AnalyticRuleSerialiser mockSerialiser;
    @Mock
    private ProcessorFilterService mockProcessorFilterService;
    @Mock
    private AnalyticRuleProcessors mockAnalyticRuleProcessors;
    @Mock
    private ExecutionScheduleDao mockExecutionScheduleDao;
    @Mock
    private DataSourceProviderRegistry mockDataSourceProviderRegistry;
    @Mock
    private SearchRequestFactory mockSearchRequestFactory;

    private AnalyticRuleStoreImpl analyticRuleStore;

    @BeforeEach
    void setUp() {
        Mockito.doReturn(mockStore)
                .when(mockStoreFactory)
                .createStore(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(mockSecurityContext.hasDocumentPermission(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(mockProcessorFilterService.find(Mockito.any(DocRef.class)))
                .thenReturn(ResultPage.<ProcessorFilter>empty());
        Mockito.when(mockExecutionScheduleDao.fetchExecutionSchedule(Mockito.any()))
                .thenReturn(ResultPage.<ExecutionSchedule>empty());

        analyticRuleStore = new AnalyticRuleStoreImpl(
                mockStoreFactory,
                mockSecurityContext,
                mockSerialiser,
                () -> mockProcessorFilterService,
                () -> mockAnalyticRuleProcessors,
                () -> mockExecutionScheduleDao,
                () -> mockDataSourceProviderRegistry,
                mockSearchRequestFactory);
    }

    @Test
    void testFindAssociatedNonExplorerDocRefs_nullProcessType() {
        givenRuleWithProcessType(null);

        final Set<DocRef> docRefs = analyticRuleStore.findAssociatedNonExplorerDocRefs(RULE_DOC_REF);

        assertThat(docRefs).isEmpty();
        Mockito.verifyNoInteractions(mockProcessorFilterService, mockExecutionScheduleDao);
    }

    @Test
    void testFindAssociatedNonExplorerDocRefs_streaming() {
        givenRuleWithProcessType(AnalyticProcessType.STREAMING);

        final Set<DocRef> docRefs = analyticRuleStore.findAssociatedNonExplorerDocRefs(RULE_DOC_REF);

        assertThat(docRefs).isEmpty();
        Mockito.verify(mockProcessorFilterService).find(RULE_DOC_REF);
        Mockito.verifyNoInteractions(mockExecutionScheduleDao);
    }

    @Test
    void testFindAssociatedNonExplorerDocRefs_scheduledQuery() {
        givenRuleWithProcessType(AnalyticProcessType.SCHEDULED_QUERY);

        final Set<DocRef> docRefs = analyticRuleStore.findAssociatedNonExplorerDocRefs(RULE_DOC_REF);

        assertThat(docRefs).isEmpty();
        Mockito.verify(mockExecutionScheduleDao).fetchExecutionSchedule(Mockito.any());
        Mockito.verifyNoInteractions(mockProcessorFilterService);
    }

    @Test
    void testFindAssociatedNonExplorerDocRefs_tableBuilder() {
        givenRuleWithProcessType(AnalyticProcessType.TABLE_BUILDER);

        final Set<DocRef> docRefs = analyticRuleStore.findAssociatedNonExplorerDocRefs(RULE_DOC_REF);

        assertThat(docRefs).isEmpty();
        Mockito.verifyNoInteractions(mockProcessorFilterService, mockExecutionScheduleDao);
    }

    private void givenRuleWithProcessType(final AnalyticProcessType analyticProcessType) {
        final AnalyticRuleDoc ruleDoc = AnalyticRuleDoc.builder()
                .uuid(RULE_DOC_REF.getUuid())
                .name(RULE_DOC_REF.getName())
                .analyticProcessType(analyticProcessType)
                .build();
        Mockito.when(mockStore.readDocument(RULE_DOC_REF))
                .thenReturn(ruleDoc);
    }
}
