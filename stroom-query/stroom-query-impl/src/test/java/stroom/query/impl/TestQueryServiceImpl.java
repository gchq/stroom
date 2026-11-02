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

package stroom.query.impl;

import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.node.api.NodeInfo;
import stroom.query.api.QueryKey;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryServiceImpl {

    private final SearchEventLog searchEventLog = Mockito.mock(SearchEventLog.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);

    @Test
    void testASearchThatFailsOutsideItsOwnErrorHandlingSaysWhy() {
        // Seen: logging a new search failed (reading a finished HTTP request the thread had inherited), and the
        // caller was given no response at all, so couldn't say why its query didn't run.
        Mockito.doThrow(new IllegalStateException("Unable to read the request"))
                .when(searchEventLog).search(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());

        final DashboardSearchResponse response = service().search(QuerySearchRequest.builder()
                .query("from x")
                .queryContext(QueryContext.builder().build())
                .build());

        assertThat(response).isNotNull();
        assertThat(response.isComplete()).isTrue();
        assertThat(response.getErrorMessages())
                .containsExactly(new ErrorMessage(Severity.ERROR, "Unable to read the request"));
    }

    @Test
    void testAnInterruptedSearchSaysSo() {
        Thread.currentThread().interrupt();
        try {
            final DashboardSearchResponse response = serviceWithExecutor(command -> {
                // As a pool thread that never runs the search before the caller is interrupted.
            }).search(QuerySearchRequest.builder()
                    .queryKey(new QueryKey("q"))
                    .query("from x")
                    .build());

            assertThat(response.isComplete()).isTrue();
            assertThat(response.getQueryKey()).isEqualTo(new QueryKey("q"));
            assertThat(response.getErrorMessages())
                    .containsExactly(new ErrorMessage(Severity.ERROR, "The search was interrupted."));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Leave the test thread as it was.
            Thread.interrupted();
        }
    }

    private QueryServiceImpl service() {
        return serviceWithExecutor(Runnable::run);
    }

    private QueryServiceImpl serviceWithExecutor(final Executor executor) {
        Mockito.when(securityContext.secureResult(Mockito.<Supplier<Object>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        Mockito.when(securityContext.useAsReadResult(Mockito.<Supplier<Object>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        final ExecutorProvider executorProvider = new ExecutorProvider() {
            @Override
            public Executor get() {
                return executor;
            }

            @Override
            public Executor get(final ThreadPool threadPool) {
                return executor;
            }
        };
        return new QueryServiceImpl(null, null, searchEventLog, securityContext, new HttpServletRequestHolder(),
                executorProvider, new SimpleTaskContextFactory(), null, null, nodeInfo, null, null, null, null,
                null, null);
    }
}
