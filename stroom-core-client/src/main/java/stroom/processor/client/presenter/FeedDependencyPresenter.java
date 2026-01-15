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

package stroom.processor.client.presenter;

import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.FeedDependency;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

public class FeedDependencyPresenter
        extends MyPresenterWidget<FeedDependencyPresenter.FeedDependencyView> {

    private final FeedDependencyListPresenter feedDependencyListPresenter;


    @Inject
    public FeedDependencyPresenter(final EventBus eventBus,
                                   final FeedDependencyView view,
                                   final FeedDependencyListPresenter feedDependencyListPresenter) {
        super(eventBus, view);
        this.feedDependencyListPresenter = feedDependencyListPresenter;
        view.setFeedDependencyList(feedDependencyListPresenter.getView());
    }

    public void show(final FeedDependencies feedDependencies,
                     final Consumer<FeedDependencies> consumer) {
        feedDependencyListPresenter
                .setFeedDependencies(NullSafe.get(feedDependencies, FeedDependencies::getFeedDependencies));
        getView().setMinProcessingDelay(NullSafe.get(feedDependencies, FeedDependencies::getMinProcessingDelay));
        getView().setMaxProcessingDelay(NullSafe.get(feedDependencies, FeedDependencies::getMaxProcessingDelay));

        // Show the feed dependencies dialog.
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Set Feed Dependencies")
                .modal(true)
                .onShow(e -> feedDependencyListPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final List<FeedDependency> list = feedDependencyListPresenter.getFeedDependencies();
                        consumer.accept(FeedDependencies
                                .builder()
                                .feedDependencies(list)
                                .minProcessingDelay(getView().getMinProcessingDelay())
                                .maxProcessingDelay(getView().getMaxProcessingDelay())
                                .build());
                    } else {
                        consumer.accept(feedDependencies);
                    }
                    e.hide();
                })
                .fire();
    }

    public interface FeedDependencyView extends View {

        void setFeedDependencyList(View view);

        void setMinProcessingDelay(SimpleDuration minProcessingDelay);

        SimpleDuration getMinProcessingDelay();

        void setMaxProcessingDelay(SimpleDuration maxProcessingDelay);

        SimpleDuration getMaxProcessingDelay();
    }
}
