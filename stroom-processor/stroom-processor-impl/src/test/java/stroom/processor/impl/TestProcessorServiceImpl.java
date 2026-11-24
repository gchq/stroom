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

import stroom.processor.shared.Processor;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProcessorServiceImpl {

    private static final int PROCESSOR_ID = 10;
    private static final String PIPELINE_UUID = "pipeline-uuid";

    @Mock
    private ProcessorDao processorDao;
    @Mock
    private ProcessorTaskDao processorTaskDao;
    @Mock
    private ProcessorFilterDao processorFilterDao;
    @Mock
    private ProcessorFilterDependencies processorFilterDependencies;

    private ProcessorServiceImpl processorService;

    @BeforeEach
    void setUp() {
        processorService = new ProcessorServiceImpl(
                new MockSecurityContext(),
                processorDao,
                processorTaskDao,
                processorFilterDao,
                processorFilterDependencies);
    }

    @Test
    void testDelete_removesFilterDependencies() {
        // Regression for gh-5979: deleting a processor logically deletes its filters directly in the DAO,
        // which left their dependency edges behind showing the deleted pipeline as missing.
        final Set<String> filterUuids = Set.of("filter-uuid-1", "filter-uuid-2");
        when(processorDao.logicalDeleteByProcessorId(PROCESSOR_ID)).thenReturn(1);
        when(processorFilterDao.fetchUuidsByProcessorId(PROCESSOR_ID)).thenReturn(filterUuids);

        final boolean deleted = processorService.delete(PROCESSOR_ID);

        assertThat(deleted)
                .isTrue();
        verify(processorFilterDependencies).remove(filterUuids);
    }

    @Test
    void testDelete_nothingDeleted() {
        when(processorDao.logicalDeleteByProcessorId(PROCESSOR_ID)).thenReturn(0);

        final boolean deleted = processorService.delete(PROCESSOR_ID);

        assertThat(deleted)
                .isFalse();
        verify(processorFilterDependencies, never()).remove(anyCollection());
    }

    @Test
    void testDeleteByPipelineUuid_removesFilterDependencies() {
        // Deleting a pipeline deletes its processor this way
        final Set<String> filterUuids = Set.of("filter-uuid-1");
        when(processorDao.fetchByPipelineUuid(PIPELINE_UUID)).thenReturn(Optional.of(
                Processor.builder().id(PROCESSOR_ID).pipelineUuid(PIPELINE_UUID).build()));
        when(processorDao.logicalDeleteByProcessorId(PROCESSOR_ID)).thenReturn(1);
        when(processorFilterDao.fetchUuidsByProcessorId(PROCESSOR_ID)).thenReturn(filterUuids);

        final boolean deleted = processorService.deleteByPipelineUuid(PIPELINE_UUID);

        assertThat(deleted)
                .isTrue();
        verify(processorFilterDependencies).remove(filterUuids);
    }
}
