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

package stroom.document.client;

import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.security.client.api.ClientSecurityContext;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPlugin {

    private static final DocRef DOC_REF = new DocRef("Feed", "f1", "Feed A");

    private final List<DocumentEditorHost<?>> hosts = new ArrayList<>();
    private TestPlugin plugin;
    private TaskMonitorFactory taskMonitorFactory;

    @BeforeEach
    void setUp() {
        // The editor's host is a Composite, which can only be mocked with GWT's bridge disarmed
        GWTMockUtilities.disarm();
        final EventBus eventBus = new SimpleEventBus();
        plugin = new TestPlugin(eventBus);
        taskMonitorFactory = Mockito.mock(TaskMonitorFactory.class);
        Mockito.when(taskMonitorFactory.createTaskMonitor()).thenReturn(Mockito.mock(TaskMonitor.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aDocumentThatFailsToLoadLetsATabSessionCarryOn() {
        final List<String> failures = new ArrayList<>();

        plugin.open(DOC_REF, true, false, null, host -> {
        }, () -> failures.add("failed"), false, taskMonitorFactory);

        // Before, neither callback ran, so a tab session's later tabs never opened
        assertThat(failures).containsExactly("failed");
        // Its editor, never shown, is released
        Mockito.verify(hosts.get(0)).dispose();

        // And it is forgotten: opening it again makes a new editor, rather than selecting a tab
        // that was never shown
        plugin.open(DOC_REF, true, false, null, host -> {
        }, () -> failures.add("failed"), false, taskMonitorFactory);
        assertThat(hosts).hasSize(2);
    }


    // --------------------------------------------------------------------------------


    // A document type whose documents can't be loaded
    private class TestPlugin extends DocumentPlugin<Object> {

        TestPlugin(final EventBus eventBus) {
            super(eventBus,
                    new ContentManager(eventBus),
                    Mockito.mock(DocumentPluginEventManager.class),
                    Mockito.mock(ClientSecurityContext.class));
        }

        @Override
        protected DocumentEditorHost<?> createEditor() {
            final DocumentEditorHost<?> host = Mockito.mock(DocumentEditorHost.class);
            hosts.add(host);
            return host;
        }

        @Override
        public void load(final DocRef docRef,
                         final Consumer<Object> resultConsumer,
                         final RestErrorHandler errorHandler,
                         final TaskMonitorFactory taskMonitorFactory) {
            errorHandler.onError(new RestError(Mockito.mock(Method.class), new RuntimeException("Not found")));
        }

        @Override
        public void save(final DocRef docRef,
                         final Object document,
                         final Consumer<Object> resultConsumer,
                         final RestErrorHandler errorHandler,
                         final TaskMonitorFactory taskMonitorFactory) {
        }

        @Override
        protected DocRef getDocRef(final Object document) {
            return DOC_REF;
        }

        @Override
        public String getType() {
            return "Feed";
        }
    }
}
