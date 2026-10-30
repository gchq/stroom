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

package stroom.pipeline.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.explorer.client.component.DocumentChooserController;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.client.component.SaveChangedDocumentsController;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.AppRoot;
import stroom.widget.component.client.HasControllerNode;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.DirectRestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelinePlugin {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "p1", "Pipeline One");

    private final EventBus eventBus = new SimpleEventBus();
    private final FakeRestFactory restFactory = new FakeRestFactory();
    private final List<DocumentChooserController> choosers = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The plugin's resources and the chooser are made with GWT.create or are Composites, which
        // only load with GWT's bridge disarmed
        GWTMockUtilities.disarm();
        new PipelinePlugin(eventBus,
                Mockito.mock(Provider.class),
                restFactory,
                new ContentManager(eventBus),
                Mockito.mock(DocumentPluginEventManager.class),
                Mockito.mock(ClientSecurityContext.class),
                () -> {
                    final DocumentChooserController chooser = Mockito.mock(DocumentChooserController.class);
                    choosers.add(chooser);
                    return chooser;
                },
                (Provider<SaveChangedDocumentsController>) Mockito.mock(Provider.class),
                new AppRoot());
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void noChooserIsMadeUntilThePipelineIsFound() {
        beginStepping();

        // Before, the chooser was made before the pipeline was looked up, so a lookup that failed
        // left a chooser nothing owned or disposed
        assertThat(restFactory.pending).hasSize(1);
        assertThat(choosers).isEmpty();
    }

    @Test
    void theChooserIsMadeAndShownOnceThePipelineIsFound() {
        beginStepping();
        final ExplorerNode node = ExplorerNode.builder().docRef(PIPELINE).build();
        restFactory.succeed(node);

        assertThat(choosers).hasSize(1);
        Mockito.verify(choosers.get(0)).setSelectedNode(node);
        Mockito.verify(choosers.get(0)).showOnce(Mockito.any(HasControllerNode.class),
                Mockito.<Consumer<DocRef>>any(),
                Mockito.any());
    }

    private void beginStepping() {
        BeginPipelineSteppingEvent.fire(eventBus::fireEvent,
                null,
                null,
                StepType.REFRESH,
                StepLocation.first(1L),
                PIPELINE);
    }


    // --------------------------------------------------------------------------------


    // Records each request's success handler, for the test to call; nothing replies by itself
    private static class FakeRestFactory implements RestFactory {

        private final List<Consumer<Object>> pending = new ArrayList<>();

        @SuppressWarnings("unchecked")
        void succeed(final Object result) {
            final Consumer<Object> onSuccess = pending.remove(0);
            onSuccess.accept(result);
        }

        @Override
        public <T extends DirectRestService> Resource<T> create(final T service) {
            return new Resource<>() {
                @Override
                public <R> MethodExecutor<T, R> method(final Function<T, R> function) {
                    return new Executor<>();
                }

                @Override
                public MethodExecutor<T, Void> call(final Consumer<T> consumer) {
                    return new Executor<>();
                }
            };
        }

        private class Executor<T extends DirectRestService, R>
                implements MethodExecutor<T, R>, TaskExecutor<T, R> {

            private Consumer<R> onSuccess = result -> {
            };

            @Override
            public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
                return this;
            }

            @Override
            public TaskExecutor<T, R> taskMonitorFactory(final TaskMonitorFactory taskMonitorFactory,
                                                         final String taskMessage) {
                return this;
            }

            @Override
            public MethodExecutor<T, R> onSuccess(final Consumer<R> resultConsumer) {
                onSuccess = resultConsumer;
                return this;
            }

            @Override
            public MethodExecutor<T, R> onFailure(final RestErrorHandler errorHandler) {
                return this;
            }

            @Override
            public MethodExecutor<T, R> owner(final HasControllerNode owner) {
                return this;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void exec() {
                pending.add(result -> onSuccess.accept((R) result));
            }
        }
    }
}
