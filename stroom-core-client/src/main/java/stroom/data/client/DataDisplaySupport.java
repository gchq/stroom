/*
 * Copyright 2020 Crown Copyright
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

package stroom.data.client;

import stroom.data.client.component.DataViewer;
import stroom.data.client.component.DataViewerController;
import stroom.data.client.component.SourceViewerController;
import stroom.data.client.event.ShowDataEvent;
import stroom.pipeline.shared.SourceLocation;
import stroom.widget.component.client.AppRoot;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class DataDisplaySupport {

    private final Provider<DataViewerController> dataControllerProvider;
    private final Provider<DataPreviewTabPlugin> dataPreviewTabPluginProvider;

    private final Provider<SourceViewerController> sourceControllerProvider;
    private final Provider<SourceTabPlugin> sourceTabPluginProvider;

    private final AppRoot appRoot;

    @Inject
    public DataDisplaySupport(final EventBus eventBus,
                              final Provider<DataViewerController> dataControllerProvider,
                              final Provider<DataPreviewTabPlugin> dataPreviewTabPluginProvider,
                              final Provider<SourceViewerController> sourceControllerProvider,
                              final Provider<SourceTabPlugin> sourceTabPluginProvider,
                              final AppRoot appRoot) {
        this.appRoot = appRoot;

        this.dataControllerProvider = dataControllerProvider;
        this.dataPreviewTabPluginProvider = dataPreviewTabPluginProvider;
        this.sourceControllerProvider = sourceControllerProvider;
        this.sourceTabPluginProvider = sourceTabPluginProvider;

        eventBus.addHandler(ShowDataEvent.getType(), showDataEvent -> {
            switch (showDataEvent.getDisplayMode()) {
                case DIALOG:
                    openPopupDialog(showDataEvent);
                    break;
                case STROOM_TAB:
                    openStroomTab(showDataEvent);
                    break;
                default:
                    throw new RuntimeException("Unknown displayMode " + showDataEvent.getDisplayMode());
            }
        });
    }

    private void openStroomTab(final ShowDataEvent showDataEvent) {
        if (DataViewType.PREVIEW.equals(showDataEvent.getDataViewType())
                || DataViewType.INFO.equals(showDataEvent.getDataViewType())) {
            dataPreviewTabPluginProvider.get()
                    .open(showDataEvent.getSourceLocation(), showDataEvent.getDataViewType(), true);
        } else {
            sourceTabPluginProvider.get()
                    .open(showDataEvent.getSourceLocation(), true);
        }
    }

    private void openPopupDialog(final ShowDataEvent showDataEvent) {
        final SourceLocation sourceLocation = showDataEvent.getSourceLocation();
        final String caption = "Stream " + sourceLocation.getIdentifierString();
        final PopupSize popupSize = PopupSize.resizable(1400, 800);

        if (DataViewType.PREVIEW.equals(showDataEvent.getDataViewType()) ||
                DataViewType.INFO.equals(showDataEvent.getDataViewType())) {
            // One per use, the app's until it is hidden
            final DataViewerController dataViewer = dataControllerProvider.get().attach(new DataViewer());
            dataViewer.setDisplayMode(showDataEvent.getDisplayMode());
            dataViewer.setInitDataViewType(showDataEvent.getDataViewType());
            dataViewer.fetchData(sourceLocation);
            ControllerDialog.showOnce(appRoot, dataViewer)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(caption)
                    .onShow(e -> dataViewer.focus())
                    .fire();
        } else {
            final SourceViewerController sourceViewer = sourceControllerProvider.get();
            sourceViewer.setSourceLocationUsingHighlight(sourceLocation);
            ControllerDialog.showOnce(appRoot, sourceViewer)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(caption)
                    .onShow(e -> sourceViewer.focus())
                    .fire();
        }
    }
}
