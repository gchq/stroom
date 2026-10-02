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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AbstractSettingsPresenter.SettingsView;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.ReportDoc;
import stroom.config.global.client.presenter.ConfigDefaultSetter;
import stroom.config.global.shared.ConfigTarget;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

/**
 * The settings shared by every kind of rule document, i.e. those that are neither about the query nor
 * about notifying anyone of the result.
 */
public abstract class AbstractSettingsPresenter<V extends SettingsView, D extends AbstractAnalyticRuleDoc>
        extends DocPresenter<V, D>
        implements SettingsUiHandlers {

    private final DocSelectionBoxPresenter errorFeedPresenter;
    private final UiConfigCache uiConfigCache;
    private final ConfigDefaultSetter configDefaultSetter;
    private ConfigTarget configTarget = ConfigTarget.ANALYTIC_UI_DEFAULT;

    AbstractSettingsPresenter(final EventBus eventBus,
                              final V view,
                              final DocSelectionBoxPresenter errorFeedPresenter,
                              final UiConfigCache uiConfigCache,
                              final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus, view);
        this.errorFeedPresenter = errorFeedPresenter;
        this.uiConfigCache = uiConfigCache;
        this.configDefaultSetter = configDefaultSetter;
        view.setUiHandlers(this);
        errorFeedPresenter.setIncludedTypes(FeedDoc.TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        getView().setErrorFeedView(errorFeedPresenter.getView());
        // Only an administrator can change a global property, so don't offer it to anyone else.
        getView().setSetDefaultVisible(configDefaultSetter.isAllowed());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onChange()));
    }

    @Override
    protected void onRead(final DocRef docRef, final D doc, final boolean readOnly) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                final boolean isReport = ReportDoc.TYPE.equals(docRef.getType());
                configTarget = isReport
                        ? ConfigTarget.REPORT_UI_DEFAULT
                        : ConfigTarget.ANALYTIC_UI_DEFAULT;
                DocRef selectedDocRef = doc.getErrorFeed();
                if (selectedDocRef == null) {
                    if (isReport) {
                        selectedDocRef = extendedUiConfig.getReportUiDefaultConfig().getDefaultErrorFeed();
                    } else {
                        selectedDocRef = extendedUiConfig.getAnalyticUiDefaultConfig().getDefaultErrorFeed();
                    }
                }
                if (selectedDocRef != null) {
                    errorFeedPresenter.setSelectedEntityReference(selectedDocRef, true);
                }
            }
        }, this);
    }

    @Override
    public void onSetDefaultErrorFeed() {
        configDefaultSetter.setDefault(
                this,
                configTarget,
                AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_ERROR_FEED,
                errorFeedPresenter.getSelectedEntityReference(),
                "error feed",
                this);
    }

    DocRef getErrorFeed() {
        return errorFeedPresenter.getSelectedEntityReference();
    }

    // --------------------------------------------------------------------------------


    public interface SettingsView extends View, HasUiHandlers<SettingsUiHandlers> {

        void setErrorFeedView(View view);

        void setSetDefaultVisible(boolean visible);
    }
}
