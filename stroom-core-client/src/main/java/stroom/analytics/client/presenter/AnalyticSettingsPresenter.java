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
import stroom.analytics.client.presenter.AnalyticSettingsPresenter.AnalyticSettingsView;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
import stroom.config.global.client.presenter.ConfigDefaultSetter;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AnalyticSettingsPresenter
        extends AbstractSettingsPresenter<AnalyticSettingsView, AnalyticRuleDoc> {

    @Inject
    public AnalyticSettingsPresenter(final EventBus eventBus,
                                     final AnalyticSettingsView view,
                                     final DocSelectionBoxPresenter errorFeedPresenter,
                                     final UiConfigCache uiConfigCache,
                                     final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus, view, errorFeedPresenter, uiConfigCache, configDefaultSetter);
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
        getView().setLevel(document.getLevel());
        getView().setStatus(document.getStatus());
        getView().setIncludeRuleDocumentation(document.isIncludeRuleDocumentation());
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        return document
                .copy()
                .errorFeed(getErrorFeed())
                .level(getView().getLevel())
                .status(getView().getStatus())
                .includeRuleDocumentation(getView().isIncludeRuleDocumentation())
                .build();
    }

    // --------------------------------------------------------------------------------


    public interface AnalyticSettingsView extends SettingsView, Focus {

        AnalyticRuleLevel getLevel();

        void setLevel(AnalyticRuleLevel level);

        AnalyticRuleStatus getStatus();

        void setStatus(AnalyticRuleStatus status);

        boolean isIncludeRuleDocumentation();

        void setIncludeRuleDocumentation(boolean includeRuleDocumentation);
    }
}
