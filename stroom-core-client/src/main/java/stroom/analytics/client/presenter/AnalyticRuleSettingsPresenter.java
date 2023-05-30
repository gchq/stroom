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

import stroom.analytics.client.presenter.AnalyticRuleSettingsPresenter.AnalyticRuleSettingsView;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AnalyticRuleSettingsPresenter
        extends DocumentEditPresenter<AnalyticRuleSettingsView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    @Inject
    public AnalyticRuleSettingsPresenter(final EventBus eventBus,
                                         final AnalyticRuleSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc alertRule, final boolean readOnly) {
        getView().setDescription(alertRule.getDescription());
        getView().setLanguageVersion(alertRule.getLanguageVersion());
        getView().setAnalyticRuleType(alertRule.getAnalyticRuleType());
        getView().setDataRetention(alertRule.getDataRetention());
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc alertRule) {
        return alertRule.copy()
                .description(getView().getDescription())
                .languageVersion(getView().getLanguageVersion())
                .analyticRuleType(getView().getAnalyticRuleType())
                .dataRetention(getView().getDataRetention())
                .build();
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.DOCUMENT_TYPE;
    }

    public interface AnalyticRuleSettingsView extends View, HasUiHandlers<DirtyUiHandlers> {

        String getDescription();

        void setDescription(final String description);

        QueryLanguageVersion getLanguageVersion();

        void setLanguageVersion(final QueryLanguageVersion languageVersion);

        AnalyticRuleType getAnalyticRuleType();

        void setAnalyticRuleType(AnalyticRuleType analyticRuleType);

        SimpleDuration getDataRetention();

        void setDataRetention(SimpleDuration dataRetention);
    }
}
