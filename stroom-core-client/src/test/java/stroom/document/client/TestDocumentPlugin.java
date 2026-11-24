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
import stroom.document.client.editor.DocumentEditorHost;
import stroom.document.client.editor.SaveQueue;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;

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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void aDocumentSavedIsReadBack() {
        final DocumentEditorHost<Object> host = dirtyHost();

        plugin.save(host);
        plugin.saves.get(0).accept("saved");

        Mockito.verify(host).readSaved(DOC_REF, "written", "saved", false);
    }

    @Test
    void aDocumentChangedWhileItIsSavedKeepsItsChanges() {
        final DocumentEditorHost<Object> host = dirtyHost();

        plugin.save(host);
        Mockito.when(host.hasChangedSince("written")).thenReturn(true);
        plugin.saves.get(0).accept("saved");

        Mockito.verify(host).readSaved(DOC_REF, "written", "saved", true);
    }

    @Test
    void changesMadeBeforeWhatRunsAfterASaveAreKept() {
        final DocumentEditorHost<Object> host = dirtyHost();
        Mockito.when(host.getPostSaveCallback()).thenReturn((saved, done, failed) -> done.accept(saved));

        plugin.save(host);
        Mockito.when(host.hasChangedSince("written")).thenReturn(true);
        Mockito.when(host.write("read")).thenReturn("written since");
        plugin.saves.get(0).accept("saved");

        Mockito.verify(host).readSaved(DOC_REF, "written", "saved", true);
    }

    @Test
    void changesMadeDuringWhatRunsAfterASaveAreKept() {
        final DocumentEditorHost<Object> host = dirtyHost();
        Mockito.when(host.getPostSaveCallback()).thenReturn((saved, done, failed) -> {
            Mockito.when(host.hasChangedSince("written")).thenReturn(true);
            done.accept(saved);
        });

        plugin.save(host);
        plugin.saves.get(0).accept("saved");

        Mockito.verify(host).readSaved(DOC_REF, "written", "saved", true);
    }

    @Test
    void whatRunsAfterASaveIsNotAChange() {
        final DocumentEditorHost<Object> host = dirtyHost();
        Mockito.when(host.getPostSaveCallback()).thenReturn((saved, done, failed) -> done.accept(saved));

        plugin.save(host);
        plugin.saves.get(0).accept("saved");

        Mockito.verify(host).readSaved(DOC_REF, "written", "saved", false);
    }

    @Test
    void aSecondSaveWaitsForTheFirst() {
        // It would send the version the first one replaces, which the server refuses
        final DocumentEditorHost<Object> host = dirtyHost();

        plugin.save(host);
        plugin.save(host);
        assertThat(plugin.saves).hasSize(1);
        Mockito.when(host.hasChangedSince("written")).thenReturn(true);
        plugin.saves.get(0).accept("saved");

        assertThat(plugin.saves).hasSize(2);
    }

    @Test
    void aSecondSaveIsNotSentOnceTheTabIsClosed() {
        // The user agreed to lose it, and the document may have been opened again since
        final DocumentEditorHost<Object> host = dirtyHost();

        plugin.save(host);
        plugin.save(host);
        Mockito.when(host.isDisposed()).thenReturn(true);
        Mockito.when(host.hasChangedSince("written")).thenReturn(true);
        plugin.saves.get(0).accept("saved");

        assertThat(plugin.saves).hasSize(1);
        Mockito.verify(host, Mockito.never()).readSaved(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean());
    }

    @Test
    void aFaultWhileSavingLetsItBeSavedAgain() {
        final DocumentEditorHost<Object> host = dirtyHost();
        Mockito.when(host.hasChangedSince("written")).thenThrow(new IllegalStateException("fault"));

        plugin.save(host);
        assertThatThrownBy(() -> plugin.saves.get(0).accept("saved")).hasMessage("fault");
        plugin.save(host);

        assertThat(plugin.saves).hasSize(2);
    }

    @Test
    void aSecondSaveWithNothingMoreToSaveSendsNothing() {
        final DocumentEditorHost<Object> host = dirtyHost();

        plugin.save(host);
        plugin.save(host);
        Mockito.when(host.isDirty()).thenReturn(false);
        plugin.saves.get(0).accept("saved");

        assertThat(plugin.saves).hasSize(1);
    }

    @Test
    void whatRunsAfterASaveFailingLetsItBeSavedAgain() {
        final DocumentEditorHost<Object> host = dirtyHost();
        Mockito.when(host.getPostSaveCallback()).thenReturn((saved, done, failed) -> failed.run());

        plugin.save(host);
        plugin.saves.get(0).accept("saved");

        // From the version saved, with the editor as it was
        Mockito.verify(host).setSaved(DOC_REF, "written", "saved");
        Mockito.verify(host, Mockito.never())
                .readSaved(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean());
        plugin.save(host);
        assertThat(plugin.saves).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private static DocumentEditorHost<Object> dirtyHost() {
        final DocumentEditorHost<Object> host = Mockito.mock(DocumentEditorHost.class);
        Mockito.when(host.isDirty()).thenReturn(true);
        Mockito.when(host.getEntity()).thenReturn("read");
        Mockito.when(host.write("read")).thenReturn("written");
        Mockito.when(host.getSaveQueue()).thenReturn(new SaveQueue());
        return host;
    }


    // --------------------------------------------------------------------------------


    // A document type whose documents can't be loaded
    private class TestPlugin extends DocumentPlugin<Object> {

        // What each save's reply is given to
        private final List<Consumer<Object>> saves = new ArrayList<>();

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
            saves.add(resultConsumer);
        }

        @Override
        public void save(final DocRef docRef,
                         final Object document,
                         final BiConsumer<Object, Consumer<Object>> postSaveCallback,
                         final Consumer<Object> resultConsumer,
                         final RestErrorHandler errorHandler,
                         final TaskMonitorFactory taskMonitorFactory) {
            saves.add(postSaveCallback == null
                    ? resultConsumer
                    : saved -> postSaveCallback.accept(saved, resultConsumer));
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
