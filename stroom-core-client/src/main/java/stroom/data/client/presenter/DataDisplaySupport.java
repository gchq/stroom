/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.data.client.DataPreviewTabPlugin;
import stroom.data.client.SourceTabPlugin;
import stroom.pipeline.shared.SourceLocation;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import javax.inject.Provider;

public class DataDisplaySupport {

    private final Provider<DataPresenter> dataPresenterProvider;
    private final Provider<DataPreviewTabPlugin> dataPreviewTabPluginProvider;

    private final Provider<SourcePresenter> sourcePresenterProvider;
    private final Provider<SourceTabPlugin> sourceTabPluginProvider;

    @Inject
    public DataDisplaySupport(final EventBus eventBus,
                              final Provider<DataPresenter> dataPresenterProvider,
                              final Provider<DataPreviewTabPlugin> dataPreviewTabPluginProvider,
                              final Provider<SourcePresenter> sourcePresenterProvider,
                              final Provider<SourceTabPlugin> sourceTabPluginProvider) {

        this.dataPresenterProvider = dataPresenterProvider;
        this.dataPreviewTabPluginProvider = dataPreviewTabPluginProvider;
        this.sourcePresenterProvider = sourcePresenterProvider;
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
        final MyPresenterWidget<?> presenter;
        final String caption;

        final Focus focus;
        if (DataViewType.PREVIEW.equals(showDataEvent.getDataViewType()) ||
                DataViewType.INFO.equals(showDataEvent.getDataViewType())) {
            final DataPresenter dataPresenter = dataPresenterProvider.get();
            dataPresenter.setDisplayMode(showDataEvent.getDisplayMode());
            dataPresenter.setInitDataViewType(showDataEvent.getDataViewType());
            dataPresenter.fetchData(sourceLocation);
            presenter = dataPresenter;
            caption = "Stream "
                    + sourceLocation.getIdentifierString();
            focus = dataPresenter;
        } else {
            final SourcePresenter sourcePresenter = sourcePresenterProvider.get();
            sourcePresenter.setSourceLocationUsingHighlight(sourceLocation);
            presenter = sourcePresenter;
            // Convert to one based for UI;
            caption = "Stream " + sourceLocation.getIdentifierString();
            focus = sourcePresenter;
        }

        final PopupSize popupSize = PopupSize.resizable(1400, 800);
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> focus.focus())
                .fire();
    }
}
