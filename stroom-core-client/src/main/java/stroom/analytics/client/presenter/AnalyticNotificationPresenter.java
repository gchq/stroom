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

import stroom.analytics.client.presenter.AnalyticNotificationPresenter.AnalyticNotificationView;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationDestination;
import stroom.analytics.shared.AnalyticNotificationDestinationType;
import stroom.analytics.shared.AnalyticNotificationEmailDestination;
import stroom.analytics.shared.AnalyticNotificationStreamDestination;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AnalyticNotificationPresenter
        extends DocumentEditPresenter<AnalyticNotificationView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    private final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter;
    private final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter;

    @Inject
    public AnalyticNotificationPresenter(final EventBus eventBus,
                                         final AnalyticNotificationView view,
                                         final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter,
                                         final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.analyticStreamDestinationPresenter = analyticStreamDestinationPresenter;
        this.analyticEmailDestinationPresenter = analyticEmailDestinationPresenter;
    }

    @Override
    protected void onBind() {
        registerHandler(analyticStreamDestinationPresenter.addDirtyHandler(e -> setDirty(true)));
        registerHandler(analyticEmailDestinationPresenter.addDirtyHandler(e -> setDirty(true)));
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        final AnalyticNotificationConfig config = document.getAnalyticNotificationConfig();
        if (config != null) {
            getView().setLimitNotifications(config.isLimitNotifications());
            getView().setMaxNotifications(config.getMaxNotifications());
            getView().setResumeAfter(config.getResumeAfter());
            getView().setDestinationType(config.getDestinationType());

            if (config.getDestinationType() != null) {
                switch (config.getDestinationType()) {
                    case EMAIL: {
                        if (config.getDestination() instanceof AnalyticNotificationEmailDestination) {
                            analyticEmailDestinationPresenter.read(
                                    (AnalyticNotificationEmailDestination) config.getDestination());
                        }
                        getView().setDestinationView(analyticEmailDestinationPresenter.getView());
                        break;
                    }
                    case STREAM: {
                        if (config.getDestination() instanceof AnalyticNotificationStreamDestination) {
                            analyticStreamDestinationPresenter.read(
                                    (AnalyticNotificationStreamDestination) config.getDestination());
                        }
                        getView().setDestinationView(analyticStreamDestinationPresenter.getView());
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        AnalyticNotificationDestination destination = null;
        if (getView().getDestinationType() != null) {
            switch (getView().getDestinationType()) {
                case EMAIL: {
                    destination = analyticEmailDestinationPresenter.write();
                    break;
                }
                case STREAM: {
                    destination = analyticStreamDestinationPresenter.write();
                    break;
                }
            }
        }
        final AnalyticNotificationConfig analyticNotificationConfig = AnalyticNotificationConfig
                .builder()
                .limitNotifications(getView().isLimitNotifications())
                .maxNotifications(getView().getMaxNotifications())
                .resumeAfter(getView().getResumeAfter())
                .destinationType(getView().getDestinationType())
                .destination(destination)
                .build();
        return document.copy().analyticNotificationConfig(analyticNotificationConfig).build();
    }

    @Override
    public void onDirty() {
        if (getView().getDestinationType() != null) {
            switch (getView().getDestinationType()) {
                case EMAIL: {
                    getView().setDestinationView(analyticEmailDestinationPresenter.getView());
                    break;
                }
                case STREAM: {
                    getView().setDestinationView(analyticStreamDestinationPresenter.getView());
                    break;
                }
            }
        }
        setDirty(true);
    }

    public interface AnalyticNotificationView extends View, HasUiHandlers<DirtyUiHandlers> {

        boolean isLimitNotifications();

        void setLimitNotifications(boolean limitNotifications);

        int getMaxNotifications();

        void setMaxNotifications(int maxNotifications);

        SimpleDuration getResumeAfter();

        void setResumeAfter(SimpleDuration resumeAfter);

        AnalyticNotificationDestinationType getDestinationType();

        void setDestinationType(AnalyticNotificationDestinationType destinationType);

        void setDestinationView(View view);
    }
}
