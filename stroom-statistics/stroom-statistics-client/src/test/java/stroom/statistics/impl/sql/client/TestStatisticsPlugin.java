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

package stroom.statistics.impl.sql.client;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.ContentManager;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.editor.DocumentEditorHost;
import stroom.document.client.editor.SaveQueue;
import stroom.security.client.api.ClientSecurityContext;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsPlugin {

    private static final StatisticStoreDoc DOC = StatisticStoreDoc.builder()
            .uuid("s1")
            .name("Statistic A")
            .build();
    // A change the user is asked to confirm
    private static final StatisticStoreDoc OTHER_TYPE = DOC.copy()
            .statisticType(DOC.getStatisticType() == StatisticType.COUNT
                    ? StatisticType.VALUE
                    : StatisticType.COUNT)
            .build();

    private final List<String> alerts = new ArrayList<>();
    private final List<ConfirmEvent> confirms = new ArrayList<>();
    private DocumentEditorHost<StatisticStoreDoc> host;
    private TestPlugin plugin;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The editor's host is a Composite, which can only be mocked with GWT's bridge disarmed
        GWTMockUtilities.disarm();
        final EventBus eventBus = new SimpleEventBus();
        eventBus.addHandler(AlertEvent.getType(), event -> alerts.add(event.getMessage().asString()));
        eventBus.addHandler(ConfirmEvent.getType(), confirms::add);
        plugin = new TestPlugin(eventBus);
        host = Mockito.mock(DocumentEditorHost.class);
        Mockito.when(host.isDirty()).thenReturn(true);
        Mockito.when(host.getEntity()).thenReturn(DOC);
        Mockito.when(host.write(DOC)).thenReturn(DOC);
        Mockito.when(host.getSaveQueue()).thenReturn(new SaveQueue());
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aSecondSaveWaitsForTheFirst() {
        plugin.save(host);
        plugin.save(host);
        assertThat(plugin.loads).hasSize(1);
        plugin.loads.get(0).accept(DOC);
        assertThat(plugin.saves).hasSize(1);
        plugin.saves.get(0).accept(DOC);

        Mockito.verify(host).readSaved(Mockito.any(), Mockito.eq(DOC), Mockito.eq(DOC), Mockito.eq(false));
        assertThat(plugin.loads).hasSize(2);
    }

    @Test
    void aFailedSaveIsSaidAndCanBeTriedAgain() {
        plugin.save(host);
        plugin.loads.get(0).accept(DOC);
        plugin.saveErrors.get(0).onError(error());

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0)).startsWith("Unable to save document");
        plugin.save(host);
        assertThat(plugin.loads).hasSize(2);
    }

    @Test
    void aFailedLoadIsSaidAndTheSaveCanBeTriedAgain() {
        plugin.save(host);
        plugin.loadErrors.get(0).onError(error());

        assertThat(alerts).hasSize(1);
        plugin.save(host);
        assertThat(plugin.loads).hasSize(2);
    }

    @Test
    void aSaveNotConfirmedCanBeTriedAgain() {
        Mockito.when(host.write(DOC)).thenReturn(OTHER_TYPE);

        plugin.save(host);
        plugin.loads.get(0).accept(DOC);
        assertThat(confirms).hasSize(1);
        confirms.get(0).getCallback().onResult(false);

        assertThat(plugin.saves).isEmpty();
        plugin.save(host);
        assertThat(plugin.loads).hasSize(2);
    }

    @Test
    void nothingIsSavedOnceTheTabIsClosed() {
        Mockito.when(host.isDisposed()).thenReturn(true);

        plugin.save(host);

        assertThat(plugin.loads).isEmpty();
    }

    private static RestError error() {
        return new RestError(Mockito.mock(Method.class), new RuntimeException("No server"));
    }


    // --------------------------------------------------------------------------------


    // Keeps what each load and save is to be given
    private static class TestPlugin extends StatisticsPlugin {

        private final List<Consumer<StatisticStoreDoc>> loads = new ArrayList<>();
        private final List<RestErrorHandler> loadErrors = new ArrayList<>();
        private final List<Consumer<StatisticStoreDoc>> saves = new ArrayList<>();
        private final List<RestErrorHandler> saveErrors = new ArrayList<>();

        TestPlugin(final EventBus eventBus) {
            super(eventBus,
                    null,
                    null,
                    new ContentManager(eventBus),
                    Mockito.mock(DocumentPluginEventManager.class),
                    Mockito.mock(ClientSecurityContext.class));
        }

        @Override
        public void load(final DocRef docRef,
                         final Consumer<StatisticStoreDoc> resultConsumer,
                         final RestErrorHandler errorHandler,
                         final TaskMonitorFactory taskMonitorFactory) {
            loads.add(resultConsumer);
            loadErrors.add(errorHandler);
        }

        @Override
        public void save(final DocRef docRef,
                         final StatisticStoreDoc document,
                         final Consumer<StatisticStoreDoc> resultConsumer,
                         final RestErrorHandler errorHandler,
                         final TaskMonitorFactory taskMonitorFactory) {
            saves.add(resultConsumer);
            saveErrors.add(errorHandler);
        }
    }
}
