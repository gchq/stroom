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

package stroom.processor.client.component;

import stroom.processor.shared.FeedDependencies;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Set Feed Dependencies dialog ([FeedDependenciesEditor]): the feeds a processor filter's
/// streams wait for, and the minimum and maximum processing delays. Made for one use.
public class FeedDependenciesEditorController extends Controller {

    private final FeedDependenciesEditor view;
    private final FeedDependencyListController feedDependencyList;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param feedDependencyList The feed dependencies.
    @Inject
    public FeedDependenciesEditorController(final EventBus eventBus,
                                            final FeedDependenciesEditor view,
                                            final FeedDependencyListController feedDependencyList) {
        super(eventBus);
        this.view = view;
        this.feedDependencyList = own(feedDependencyList);
        initWidget(view);
        view.setFeedDependencyList(feedDependencyList);
    }

    /// Edits feed dependencies in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener           What shows it.
    /// @param feedDependencies The dependencies, or `null` for none.
    /// @param onOk             Given the dependencies entered, on OK.
    public void edit(final HasControllerNode opener,
                     final FeedDependencies feedDependencies,
                     final Consumer<FeedDependencies> onOk) {
        feedDependencyList
                .setFeedDependencies(NullSafe.get(feedDependencies, FeedDependencies::getFeedDependencies));
        view.setMinProcessingDelay(NullSafe.get(feedDependencies, FeedDependencies::getMinProcessingDelay));
        view.setMaxProcessingDelay(NullSafe.get(feedDependencies, FeedDependencies::getMaxProcessingDelay));

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption("Set Feed Dependencies")
                .modal(true)
                .onShow(e -> feedDependencyList.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(FeedDependencies
                                .builder()
                                .feedDependencies(feedDependencyList.getFeedDependencies())
                                .minProcessingDelay(view.getMinProcessingDelay())
                                .maxProcessingDelay(view.getMaxProcessingDelay())
                                .build());
                    }
                    e.hide();
                })
                .fire();
    }
}
