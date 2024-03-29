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
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermissionNames;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.time.SimpleDuration;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AnalyticNotificationPresenter
        extends DocumentEditPresenter<AnalyticNotificationView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    private final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter;
    private final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter;
    private final DocSelectionBoxPresenter errorFeedPresenter;
    private final UiConfigCache uiConfigCache;

    @Inject
    public AnalyticNotificationPresenter(final EventBus eventBus,
                                         final AnalyticNotificationView view,
                                         final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter,
                                         final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter,
                                         final DocSelectionBoxPresenter errorFeedPresenter,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        view.setUiHandlers(this);
        this.analyticStreamDestinationPresenter = analyticStreamDestinationPresenter;
        this.analyticEmailDestinationPresenter = analyticEmailDestinationPresenter;
        this.errorFeedPresenter = errorFeedPresenter;

        errorFeedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        getView().setErrorFeedView(errorFeedPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(analyticStreamDestinationPresenter.addDirtyHandler(e -> setDirty(true)));
        registerHandler(analyticEmailDestinationPresenter.addDirtyHandler(e -> setDirty(true)));
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        uiConfigCache.get().onSuccess(extendedUiConfig -> {
            final AnalyticNotificationConfig config = document.getAnalyticNotificationConfig();
            if (config != null) {
                getView().setLimitNotifications(config.isLimitNotifications());
                getView().setMaxNotifications(config.getMaxNotifications());
                getView().setResumeAfter(config.getResumeAfter());
                getView().setDestinationType(config.getDestinationType());
                errorFeedPresenter.setSelectedEntityReference(config.getErrorFeed());

                setDestinationPresenter(config.getDestinationType());
//                if (config.getDestinationType() != null) {
//                    switch (config.getDestinationType()) {
//                        case EMAIL: {
//                            getView().setDestinationView(analyticEmailDestinationPresenter.getView());
//                            break;
//                        }
//                        case STREAM: {
//                            getView().setDestinationView(analyticStreamDestinationPresenter.getView());
//                            break;
//                        }
//                    }
//                }
            }

            // Initialise the sub presenters whether we have config or not, so they get the right defaults
            final AnalyticUiDefaultConfig defaultConfig = extendedUiConfig.getAnalyticUiDefaultConfig();
            final AnalyticNotificationDestination destination = GwtNullSafe.get(
                    config,
                    AnalyticNotificationConfig::getDestination);

            AnalyticNotificationEmailDestination emailDestination = getOrDefaultEmailDestination(
                    destination, defaultConfig);
            GwtNullSafe.consume(emailDestination, analyticEmailDestinationPresenter::read);

            AnalyticNotificationStreamDestination streamDestination = getOrDefaultStreamDestination(
                    destination, defaultConfig);
            GwtNullSafe.consume(streamDestination, analyticStreamDestinationPresenter::read);
        });
    }

    private AnalyticNotificationEmailDestination getOrDefaultEmailDestination(
            final AnalyticNotificationDestination destination,
            final AnalyticUiDefaultConfig analyticUiDefaultConfig) {

        final AnalyticNotificationEmailDestination emailDestination;
        if (destination instanceof AnalyticNotificationEmailDestination) {
            emailDestination = (AnalyticNotificationEmailDestination) destination;
        } else {
            emailDestination = AnalyticNotificationEmailDestination.builder()
                    .subjectTemplate(analyticUiDefaultConfig.getDefaultSubjectTemplate())
                    .bodyTemplate(analyticUiDefaultConfig.getDefaultBodyTemplate())
                    .build();
        }
        return emailDestination;
    }

    private AnalyticNotificationStreamDestination getOrDefaultStreamDestination(
            final AnalyticNotificationDestination destination,
            final AnalyticUiDefaultConfig analyticUiDefaultConfig) {

        final AnalyticNotificationStreamDestination streamDestination;
        if (destination instanceof AnalyticNotificationStreamDestination) {
            streamDestination = (AnalyticNotificationStreamDestination) destination;
        } else {
            streamDestination = AnalyticNotificationStreamDestination.builder()
                    .destinationFeed(analyticUiDefaultConfig.getDefaultDestinationFeed())
                    .build();
        }
        return streamDestination;
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
                .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                .build();
        return document.copy().analyticNotificationConfig(analyticNotificationConfig).build();
    }

    private void setDestinationPresenter(final AnalyticNotificationDestinationType destinationType) {
        if (destinationType != null) {
            switch (destinationType) {
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
    }

    @Override
    public void onDirty() {
        setDestinationPresenter(getView().getDestinationType());
//        if (getView().getDestinationType() != null) {
//            switch (getView().getDestinationType()) {
//                case EMAIL: {
//                    getView().setDestinationView(analyticEmailDestinationPresenter.getView());
//                    break;
//                }
//                case STREAM: {
//                    getView().setDestinationView(analyticStreamDestinationPresenter.getView());
//                    break;
//                }
//            }
//        }
        setDirty(true);
    }


    // --------------------------------------------------------------------------------


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

        Widget getDestinationWidget();

        void setErrorFeedView(View view);
    }
}
