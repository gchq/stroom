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

import stroom.analytics.client.presenter.AnalyticRuleProcessingPresenter.AnalyticRuleProcessingView;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleProcessSettings;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AnalyticRuleProcessingPresenter
        extends DocumentEditPresenter<AnalyticRuleProcessingView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    @Inject
    public AnalyticRuleProcessingPresenter(final EventBus eventBus,
                                           final AnalyticRuleProcessingView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc alertRule, final boolean readOnly) {
        AnalyticRuleProcessSettings settings = alertRule.getProcessSettings();
        if (settings == null) {
            settings = AnalyticRuleProcessSettings.builder().build();
        }
        getView().setEnabled(settings.isEnabled());
        getView().setTimeToWaitForData(settings.getTimeToWaitForData());
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc alertRule) {
        final AnalyticRuleProcessSettings settings = AnalyticRuleProcessSettings.builder()
                .enabled(getView().isEnabled())
                .timeToWaitForData(getView().getTimeToWaitForData())
                .build();

        return alertRule.copy()
                .processSettings(settings)
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

    public interface AnalyticRuleProcessingView extends View, HasUiHandlers<DirtyUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);
    }
}
