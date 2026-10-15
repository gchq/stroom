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

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.BulkProcessorFilterChangeRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.query.api.ExpressionOperator;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestProcessorFilterResourceImpl {

    private static final ExpressionOperator EXPRESSION = ExpressionOperator.builder().build();

    @Mock
    private ProcessorFilterService processorFilterService;

    private ProcessorFilterResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new ProcessorFilterResourceImpl(() -> processorFilterService, MockSecurityContext::new);
    }

    @Test
    void testBulkChangeCountIsTheFiltersTheChangeWouldChange() {
        // Regression test (gwt-bugs #59): Batch Change confirmed with the processor list's row total,
        // which counts each processor's row as well as its filters', so one filter read as "2
        // processors". The count is now the filters the change applies to.
        givenFilters(filter(4));

        final Integer count = resource.getBulkChangeCount(request(ProcessorFilterChange.DISABLE));

        assertThat(count)
                .isEqualTo(1);
        verify(processorFilterService, never()).setEnabled(anyInt(), any());
    }

    @Test
    void testBulkChangeCountAndChangeSelectTheSameFilters() {
        givenFilters(filter(4), filter(5), filter(6));

        final Integer count = resource.getBulkChangeCount(request(ProcessorFilterChange.DISABLE));
        resource.bulkChange(request(ProcessorFilterChange.DISABLE));

        assertThat(count)
                .isEqualTo(3);
        verify(processorFilterService).setEnabled(4, false);
        verify(processorFilterService).setEnabled(5, false);
        verify(processorFilterService).setEnabled(6, false);
    }

    @Test
    void testBulkChangeCountIsZeroWhenNothingMatches() {
        givenFilters();

        assertThat(resource.getBulkChangeCount(request(ProcessorFilterChange.ENABLE)))
                .isZero();
    }

    @Test
    void testBulkChangeCountUsesTheRequestsExpressionUnpaged() {
        givenFilters(filter(4));

        resource.getBulkChangeCount(request(ProcessorFilterChange.ENABLE));

        final ArgumentCaptor<ExpressionCriteria> criteria = ArgumentCaptor.forClass(ExpressionCriteria.class);
        verify(processorFilterService).find(criteria.capture());
        assertThat(criteria.getValue().getExpression())
                .isEqualTo(EXPRESSION);
        // A page would cap the count below what the change, which reads every match, would change.
        assertThat(criteria.getValue().getPageRequest())
                .isEqualTo(PageRequest.unlimited());
    }

    private void givenFilters(final ProcessorFilter... filters) {
        when(processorFilterService.find(any(ExpressionCriteria.class)))
                .thenReturn(ResultPage.createUnboundedList(List.of(filters)));
    }

    private static ProcessorFilter filter(final int id) {
        return ProcessorFilter.builder().id(id).build();
    }

    private static BulkProcessorFilterChangeRequest request(final ProcessorFilterChange change) {
        return new BulkProcessorFilterChangeRequest(EXPRESSION, change, null);
    }
}
