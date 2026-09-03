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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.FindTracesWithHistogramCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every trace query endpoint must resolve its document through {@link TracesDocLoader}, which is where
 * the caller's USE permission is applied, before it reads anything.
 *
 * <p>Refusing the load and asserting the refusal reaches the caller proves the endpoint consults the
 * loader at all; asserting the shard manager and shard locator were never touched proves it does so
 * before reading. A future endpoint that read a shard first would fail here.
 */
class TestTracesQueryAuthorisation {

    private static final DocRef DOC_REF = DocRef.builder()
            .type(TracesDoc.TYPE)
            .uuid("trace-doc-uuid")
            .name("My Traces")
            .build();
    private static final String TRACE_ID = "0123456789abcdef";

    @Mock
    private TracesDocLoader docLoader;
    @Mock
    private ShardManager shardManager;
    @Mock
    private ArchiveShardLocator archiveShardLocator;
    @Mock
    private MergedCheckpointCache mergedCheckpointCache;
    @Mock
    private ExpressionPredicateFactory expressionPredicateFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private stroom.task.api.TaskContextFactory taskContextFactory;

    private SharedFileTracesStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(executorProvider.get()).thenReturn((Executor) Runnable::run);
        // The real factory hands back a Supplier the caller invokes on its own thread; run the task
        // body inline so a refusal surfaces from the store method itself.
        when(taskContextFactory.contextResult(anyString(), any())).thenAnswer(invocation -> {
            final Function<TaskContext, Object> function = invocation.getArgument(1);
            return (Supplier<Object>) () -> function.apply(null);
        });
        when(docLoader.getPlanBDoc(any()))
                .thenThrow(new PermissionException(null, "You are not authorised to read " + DOC_REF));

        store = new SharedFileTracesStore(
                docLoader,
                shardManager,
                archiveShardLocator,
                mergedCheckpointCache,
                expressionPredicateFactory,
                securityContext,
                executorProvider,
                taskContextFactory);
    }

    private void assertRefused(final ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOf(PermissionException.class);
        verify(docLoader).getPlanBDoc(DOC_REF);
        verifyNoInteractions(shardManager, archiveShardLocator);
    }

    @Test
    void findTracesIsRefused() {
        assertRefused(() -> store.findTraces(criteria()));
    }

    @Test
    void findTracesWithHistogramIsRefused() {
        assertRefused(() -> store.findTracesWithHistogram(
                new FindTracesWithHistogramCriteria(criteria(), 50)));
    }

    @Test
    void getTraceIsRefused() {
        assertRefused(() -> store.getTrace(new GetTraceRequest(DOC_REF, TRACE_ID, null, null)));
    }

    @Test
    void getSpansIsRefused() {
        assertRefused(() -> store.getSpans(
                new GetSpansRequest(DOC_REF, TRACE_ID, 0, 100, null, null, null)));
    }

    @Test
    void getTraceOverviewIsRefused() {
        assertRefused(() -> store.getTraceOverview(
                new GetTraceOverviewRequest(DOC_REF, TRACE_ID, 0L, Long.MAX_VALUE, 100)));
    }

    private static FindTraceCriteria criteria() {
        return new FindTraceCriteria(new PageRequest(0, 100), List.of(), DOC_REF, null);
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void run();
    }
}
