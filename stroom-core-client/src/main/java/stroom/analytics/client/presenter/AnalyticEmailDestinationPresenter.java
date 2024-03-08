/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter.AnalyticEmailDestinationView;
import stroom.analytics.shared.AnalyticNotificationEmailDestination;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.document.client.event.HasDirtyHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AnalyticEmailDestinationPresenter
        extends MyPresenterWidget<AnalyticEmailDestinationView>
        implements DirtyUiHandlers, HasDirtyHandlers {

    @Inject
    public AnalyticEmailDestinationPresenter(final EventBus eventBus,
                                             final AnalyticEmailDestinationView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AnalyticNotificationEmailDestination destination) {
        if (destination != null) {
            getView().setTo(destination.getTo());
            getView().setCc(destination.getCc());
            getView().setBcc(destination.getBcc());
        }
    }

    public AnalyticNotificationEmailDestination write() {
        return AnalyticNotificationEmailDestination
                .builder()
                .to(getView().getTo())
                .cc(getView().getCc())
                .bcc(getView().getBcc())
                .build();
    }

    @Override
    public void onDirty() {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface AnalyticEmailDestinationView extends View, HasUiHandlers<DirtyUiHandlers> {

        String getTo();

        void setTo(String to);

        String getCc();

        void setCc(String cc);

        String getBcc();

        void setBcc(String bcc);
    }
}
