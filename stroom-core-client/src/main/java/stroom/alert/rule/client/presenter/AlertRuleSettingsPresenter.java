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

import stroom.alert.rule.client.presenter.AlertRuleSettingsPresenter.AlertRuleSettingsView;
import stroom.alert.rule.shared.AbstractAlertRule;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleType;
import stroom.alert.rule.shared.QueryLanguageVersion;
import stroom.alert.rule.shared.ThresholdAlertRule;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermissionNames;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;
import javax.inject.Provider;

public class AlertRuleSettingsPresenter
        extends DocumentSettingsPresenter<AlertRuleSettingsView, AlertRuleDoc>
        implements DirtyUiHandlers {

    private final EditorPresenter codePresenter;
    private final EntityDropDownPresenter feedPresenter;
    private boolean readOnly = true;

    private DocRef destinationFeed;

    @Inject
    public AlertRuleSettingsPresenter(final EventBus eventBus,
                                      final AlertRuleSettingsView view,
                                      final Provider<EditorPresenter> editorPresenterProvider,
                                      final EntityDropDownPresenter feedPresenter) {
        super(eventBus, view);
        this.feedPresenter = feedPresenter;

        feedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);

        codePresenter = editorPresenterProvider.get();
        codePresenter.setMode(AceEditorMode.STROOM_QUERY);
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
//            codePresenter.setReadOnly(readOnly);
        codePresenter.getFormatAction().setAvailable(!readOnly);
        if (getEntity() != null && getEntity().getQuery() != null) {
            codePresenter.setText(getEntity().getQuery());
        }

        view.setUiHandlers(this);
        view.setQueryWidget(codePresenter.getWidget());
        view.setDestinationFeedView(feedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(feedPresenter.addDataSelectionHandler(selection -> {
            if (!Objects.equals(feedPresenter.getSelectedEntityReference(), destinationFeed)) {
                setDirty(true);
                destinationFeed = feedPresenter.getSelectedEntityReference();
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final AlertRuleDoc alertRule) {
        getView().setDescription(alertRule.getDescription());
        getView().setLanguageVersion(alertRule.getLanguageVersion());
        if (alertRule.getQuery() != null) {
            codePresenter.setText(alertRule.getQuery());
        }
        getView().setEnabled(alertRule.isEnabled());
        getView().setAlertRuleType(alertRule.getAlertRuleType());

        final AbstractAlertRule abstractAlertRule = alertRule.getAlertRule();
        if (abstractAlertRule instanceof ThresholdAlertRule) {
            final ThresholdAlertRule thresholdAlertRule = (ThresholdAlertRule) abstractAlertRule;
            getView().setTimeField(thresholdAlertRule.getTimeField());
            getView().setExecutionDelay(thresholdAlertRule.getExecutionDelay());
            getView().setExecutionFrequency(thresholdAlertRule.getExecutionFrequency());
            destinationFeed = thresholdAlertRule.getDestinationFeed();
            feedPresenter.setSelectedEntityReference(destinationFeed);
        }
    }

    @Override
    protected AlertRuleDoc onWrite(final AlertRuleDoc alertRule) {
        AbstractAlertRule rule = null;
        if (AlertRuleType.AGGREGATE.equals(getView().getAlertRuleType())) {
            rule = ThresholdAlertRule.builder()
                    .timeField(getView().getTimeField())
                    .executionDelay(getView().getExecutionDelay())
                    .executionFrequency(getView().getExecutionFrequency())
                    .destinationFeed(feedPresenter.getSelectedEntityReference())
                    .build();
        }

        return alertRule.copy()
                .description(getView().getDescription())
                .languageVersion(getView().getLanguageVersion())
                .query(codePresenter.getText())
                .enabled(getView().isEnabled())
                .alertRuleType(getView().getAlertRuleType())
                .alertRule(rule)
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

    public interface AlertRuleSettingsView extends View, HasUiHandlers<DirtyUiHandlers> {

        String getDescription();

        void setDescription(final String description);

        QueryLanguageVersion getLanguageVersion();

        void setLanguageVersion(final QueryLanguageVersion languageVersion);

        void setQueryWidget(Widget widget);

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        AlertRuleType getAlertRuleType();

        void setAlertRuleType(AlertRuleType alertRuleType);

        String getTimeField();

        void setTimeField(String timeField);

        String getExecutionDelay();

        void setExecutionDelay(String executionDelay);

        String getExecutionFrequency();

        void setExecutionFrequency(String executionFrequency);

        void setDestinationFeedView(View view);
    }
}
