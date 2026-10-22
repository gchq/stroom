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

package stroom.dashboard.client.table;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.query.client.DataSourceClient;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestBasicTableSettingsPresenter {

    private static final DocRef DATA_SOURCE = new DocRef("Index", "index-uuid", "Index");
    private static final DocRef DEFAULT_PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Default");

    private BasicTableSettingsView view;
    private DocSelectionBoxPresenter pipelinePresenter;
    private DataSourceClient dataSourceClient;
    private BasicTableSettingsPresenter presenter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        GWTMockUtilities.disarm();
        view = Mockito.mock(BasicTableSettingsView.class);
        pipelinePresenter = Mockito.mock(DocSelectionBoxPresenter.class);
        dataSourceClient = Mockito.mock(DataSourceClient.class);
        Mockito.doAnswer(invocation -> {
            final Consumer<DocRef> consumer = invocation.getArgument(1);
            consumer.accept(DEFAULT_PIPELINE);
            return null;
        }).when(dataSourceClient).fetchDefaultExtractionPipeline(eq(DATA_SOURCE), any(), any());
        presenter = new BasicTableSettingsPresenter(
                new SimpleEventBus(),
                view,
                pipelinePresenter,
                Mockito.mock(UiConfigCache.class),
                dataSourceClient);
        presenter.setDashboardContext(Mockito.mock(DashboardContext.class, Mockito.RETURNS_DEEP_STUBS));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void onUseDefaultExtractionPipeline_setsTheDefault() {
        // Regression test: ticking the box only disabled Extraction Pipeline, so the table kept
        // using its old pipeline until its query's data source changed
        presenter.read(createComponentConfig(DATA_SOURCE));
        Mockito.clearInvocations(pipelinePresenter);
        Mockito.when(view.isUseDefaultExtractionPipeline()).thenReturn(true);

        presenter.onUseDefaultExtractionPipeline(true);

        Mockito.verify(pipelinePresenter).setEnabled(false);
        Mockito.verify(pipelinePresenter).setSelectedEntityReference(DEFAULT_PIPELINE, true);
    }

    @Test
    void onUseDefaultExtractionPipeline_untickedKeepsThePipeline() {
        presenter.read(createComponentConfig(DATA_SOURCE));
        Mockito.clearInvocations(pipelinePresenter);

        presenter.onUseDefaultExtractionPipeline(false);

        Mockito.verify(pipelinePresenter).setEnabled(true);
        Mockito.verify(pipelinePresenter, Mockito.never()).setSelectedEntityReference(any(), Mockito.anyBoolean());
        Mockito.verifyNoInteractions(dataSourceClient);
    }

    @Test
    void onUseDefaultExtractionPipeline_noDataSource() {
        // A table whose query has never run has no data source to ask
        presenter.read(createComponentConfig(null));
        Mockito.clearInvocations(pipelinePresenter);

        presenter.onUseDefaultExtractionPipeline(true);

        Mockito.verifyNoInteractions(dataSourceClient);
        Mockito.verify(pipelinePresenter, Mockito.never()).setSelectedEntityReference(any(), Mockito.anyBoolean());
    }

    private ComponentConfig createComponentConfig(final DocRef dataSource) {
        return ComponentConfig.builder()
                .id("table-id")
                .name("Table")
                .settings(TableComponentSettings.builder()
                        .dataSourceRef(dataSource)
                        .build())
                .build();
    }
}
