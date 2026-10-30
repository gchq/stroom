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

package stroom.document.asset.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.dispatch.client.RestFactory.MethodExecutor;
import stroom.dispatch.client.RestFactory.Resource;
import stroom.dispatch.client.RestFactory.TaskExecutor;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.document.asset.shared.DocumentAssetSaveAsParameters;
import stroom.editor.client.component.CodeEditorController;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestDocumentAssetsTabController {

    private final List<Function<DocumentAssetResource, Object>> requests = new ArrayList<>();
    private DocumentAssetsTabController<VisualisationDoc> controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // The controller makes its REST resource with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        final RestFactory restFactory = Mockito.mock(RestFactory.class);
        final Resource<DocumentAssetResource> resource = Mockito.mock(Resource.class);
        final MethodExecutor<DocumentAssetResource, Object> executor =
                Mockito.mock(MethodExecutor.class, Mockito.RETURNS_SELF);
        Mockito.when(executor.taskMonitorFactory(any(TaskMonitorFactory.class)))
                .thenReturn(Mockito.mock(TaskExecutor.class));
        Mockito.when(restFactory.create(any())).thenReturn((Resource) resource);
        Mockito.when(resource.method(any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return executor;
        });

        final CodeEditorController codeEditor = Mockito.mock(CodeEditorController.class);
        Mockito.when(codeEditor.attach(any())).thenReturn(codeEditor);
        Mockito.when(codeEditor.addValueChangeHandler(any())).thenReturn(Mockito.mock(HandlerRegistration.class));
        Mockito.when(codeEditor.addFormatListener(any())).thenReturn(Mockito.mock(HandlerRegistration.class));

        final DocumentAssetsTab view = Mockito.mock(DocumentAssetsTab.class);
        Mockito.when(view.getTree()).thenReturn(Mockito.mock(DocumentAssetTree.class));

        controller = new DocumentAssetsTabController<>(
                new SimpleEventBus(),
                view,
                restFactory,
                () -> null,
                () -> null,
                () -> null,
                () -> codeEditor);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void saveAsCopiesTheSourcesAssetsWhenTheTabWasNeverShown() {
        final VisualisationDoc source = VisualisationDoc.builder().uuid("source").name("Source").build();
        final VisualisationDoc copy = VisualisationDoc.builder().uuid("copy").name("Copy").build();

        // The tab was never shown, so it was never given the document. Before, Save As read the
        // document from the tab, and failed with nothing to copy from
        controller.onSaveAs(source, copy, saved -> {
        });

        assertThat(requests).hasSize(1);
        final DocumentAssetResource service = Mockito.mock(DocumentAssetResource.class);
        requests.get(0).apply(service);
        final ArgumentCaptor<DocumentAssetSaveAsParameters> params =
                ArgumentCaptor.forClass(DocumentAssetSaveAsParameters.class);
        Mockito.verify(service).saveAs(eq("source"), params.capture());
        assertThat(params.getValue().getToOwnerDocId()).isEqualTo("copy");
    }
}
