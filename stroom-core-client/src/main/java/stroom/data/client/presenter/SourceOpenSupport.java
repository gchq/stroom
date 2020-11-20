/*
 * Copyright 2016 Crown Copyright
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

import stroom.data.client.SourceTabPlugin;
import stroom.pipeline.shared.SourceLocation;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class SourceOpenSupport {

    private final Provider<ClassificationWrappedSourcePresenter> sourcePresenterProvider;
    private final Provider<SourceTabPlugin> sourceTabPluginProvider;

    @Inject
    public SourceOpenSupport(final EventBus eventBus,
                             final Provider<ClassificationWrappedSourcePresenter> sourcePresenterProvider,
                             final Provider<SourceTabPlugin> sourceTabPluginProvider) {
        this.sourcePresenterProvider = sourcePresenterProvider;
        this.sourceTabPluginProvider = sourceTabPluginProvider;

        eventBus.addHandler(ShowSourceEvent.getType(), showSourceEvent -> {

            switch (showSourceEvent.getMode()) {
                case DIALOG:
                    openDialog(showSourceEvent.getSourceLocation());
                    break;
                case STROOM_TAB:
                    openStroomTab(showSourceEvent.getSourceLocation());
                    break;
                default:
                    throw new RuntimeException("Unknown type " + showSourceEvent.getMode());
            }
        });
    }

    private void openStroomTab(final SourceLocation sourceLocation) {
        sourceTabPluginProvider.get()
                .open(sourceLocation, true);
    }

    private void openDialog(final SourceLocation sourceLocation) {
        final ClassificationWrappedSourcePresenter sourcePresenter = sourcePresenterProvider.get();

        sourcePresenter.setSourceLocationUsingHighlight(sourceLocation);

        final PopupSize popupSize = new PopupSize(
                1400,
                800,
                1000,
                600,
                true);

//            final String locationInfo;
//            if (e.getSourceLocation().getDataRange() != null
//                    && e.getSourceLocation().getDataRange().getLocationFrom() != null
//                    && e.getSourceLocation().getDataRange().getLocationTo() != null) {
//                final DataRange dataRange = e.getSourceLocation().getDataRange();
//                locationInfo = " (Line:Col Range: "
//                        + dataRange.getLocationFrom().toString() + " to "
//                        + dataRange.getLocationTo().toString()
//                        + ")";
//            } else {
//                locationInfo = "";
//            }

        // Convert to one based for UI;
        final String caption = "Stream "
                + sourceLocation.getId() + ":"
                + (sourceLocation.getPartNo() + 1) + ":"
                + (sourceLocation.getSegmentNo() + 1);
//                    + locationInfo;

        ShowPopupEvent.fire(
                sourcePresenter,
                sourcePresenter,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                caption,
                null);
    }
}
