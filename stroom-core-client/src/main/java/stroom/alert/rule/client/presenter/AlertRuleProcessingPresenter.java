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

package stroom.alert.rule.client.presenter;

import stroom.alert.rule.client.presenter.AlertRuleProcessingPresenter.AlertRuleProcessingView;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleProcessSettings;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class AlertRuleProcessingPresenter
        extends DocumentSettingsPresenter<AlertRuleProcessingView, AlertRuleDoc>
        implements DirtyUiHandlers {

    @Inject
    public AlertRuleProcessingPresenter(final EventBus eventBus,
                                        final AlertRuleProcessingView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    @Override
    protected void onRead(final DocRef docRef, final AlertRuleDoc alertRule) {
        AlertRuleProcessSettings settings = alertRule.getProcessSettings();
        if (settings == null) {
            settings = AlertRuleProcessSettings.builder().build();
        }
        getView().setEnabled(settings.isEnabled());
        getView().setTimeToWaitForData(settings.getTimeToWaitForData());
    }

    @Override
    protected AlertRuleDoc onWrite(final AlertRuleDoc alertRule) {
        final AlertRuleProcessSettings settings = AlertRuleProcessSettings.builder()
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
        return AlertRuleDoc.DOCUMENT_TYPE;
    }

    public interface AlertRuleProcessingView extends View, HasUiHandlers<DirtyUiHandlers> {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);
    }
}
