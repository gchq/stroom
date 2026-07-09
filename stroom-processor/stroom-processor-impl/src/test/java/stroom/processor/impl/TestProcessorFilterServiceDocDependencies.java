/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.meta.api.MetaService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.security.mock.MockSecurityContext;
import stroom.security.user.api.UserRefLookup;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ProcessorFilterServiceImpl} keeps the doc_dependency store current on
 * create/update/delete by extracting the filter's dependencies directly from the in-hand object and
 * calling {@link DocDependencyService#setDependencies} (once, on this node) — rather than re-reading
 * the filter back via the handler registry or relying on a cluster-wide entity event.
 */
class TestProcessorFilterServiceDocDependencies {

    private static final String FILTER_UUID = "filter-uuid";
    private static final String PIPELINE_UUID = "pipe-uuid";
    private static final DocRef EXPECTED_REF = new DocRef(ProcessorFilter.ENTITY_TYPE, FILTER_UUID);

    private final ProcessorFilterDao processorFilterDao = mock(ProcessorFilterDao.class);
    private final ProcessorTaskDao processorTaskDao = mock(ProcessorTaskDao.class);
    private final DocDependencyService docDependencyService = mock(DocDependencyService.class);

    private final ProcessorFilterServiceImpl service = new ProcessorFilterServiceImpl(
            mock(ProcessorService.class),
            processorFilterDao,
            processorTaskDao,
            mock(MetaService.class),
            new MockSecurityContext(),
            mock(DocFinder.class),
            mock(UserRefLookup.class),
            () -> docDependencyService);

    private ProcessorFilter filterWithProcessor() {
        return ProcessorFilter.builder()
                .uuid(FILTER_UUID)
                .processor(Processor.builder().pipelineUuid(PIPELINE_UUID).build())
                .queryData(QueryData.builder().build())
                .build();
    }

    @Test
    void create_setsDependencies() {
        when(processorFilterDao.create(any())).thenReturn(filterWithProcessor());

        service.create(filterWithProcessor());

        // Edges are stored from the extracted filter (its pipeline is one of them), not a re-read.
        verify(docDependencyService, times(1)).setDependencies(
                eq(EXPECTED_REF),
                argThat(deps -> deps.stream().anyMatch(d -> PIPELINE_UUID.equals(d.getUuid()))));
        verify(docDependencyService, never()).removeDependencies(any());
    }

    @Test
    void update_setsDependencies() {
        when(processorFilterDao.update(any())).thenReturn(filterWithProcessor());

        service.update(filterWithProcessor());

        verify(docDependencyService, times(1)).setDependencies(
                eq(EXPECTED_REF),
                argThat(deps -> deps.stream().anyMatch(d -> PIPELINE_UUID.equals(d.getUuid()))));
        verify(docDependencyService, never()).removeDependencies(any());
    }

    @Test
    void delete_removesDependencies() {
        when(processorFilterDao.fetch(anyInt())).thenReturn(Optional.of(filterWithProcessor()));
        when(processorFilterDao.logicalDeleteByProcessorFilterId(anyInt())).thenReturn(1);

        service.delete(123);

        verify(docDependencyService, times(1)).removeDependencies(EXPECTED_REF);
        verify(docDependencyService, never()).setDependencies(any(), any());
    }
}
