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

import stroom.analytics.client.presenter.AnalyticRuleSettingsPresenter.AlertRuleSettingsView;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.analytics.shared.QueryLanguageVersion;
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

public class AnalyticRuleSettingsPresenter
        extends DocumentSettingsPresenter<AlertRuleSettingsView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    private final EditorPresenter codePresenter;
    private final EntityDropDownPresenter feedPresenter;

    private DocRef currentFeed;

    @Inject
    public AnalyticRuleSettingsPresenter(final EventBus eventBus,
                                         final AlertRuleSettingsView view,
                                         final Provider<EditorPresenter> editorPresenterProvider,
                                         final EntityDropDownPresenter feedPresenter) {
        super(eventBus, view);
        this.feedPresenter = feedPresenter;
        codePresenter = editorPresenterProvider.get();
        codePresenter.setMode(AceEditorMode.STROOM_QUERY);
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(codePresenter.addFormatHandler(event -> setDirty(true)));
//            codePresenter.setReadOnly(readOnly);
//        codePresenter.getFormatAction().setAvailable(!readOnly);
        if (getEntity() != null && getEntity().getQuery() != null) {
            codePresenter.setText(getEntity().getQuery());
        }

        view.setUiHandlers(this);
        view.setQueryWidget(codePresenter.getWidget());

        feedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        view.setDestinationFeedView(feedPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(codePresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(feedPresenter.addDataSelectionHandler(event -> {
            if (!Objects.equals(feedPresenter.getSelectedEntityReference(), currentFeed)) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc alertRule) {
        getView().setDescription(alertRule.getDescription());
        getView().setLanguageVersion(alertRule.getLanguageVersion());
        codePresenter.setText(alertRule.getQuery());
        getView().setAlertRuleType(alertRule.getAlertRuleType());
        currentFeed = alertRule.getDestinationFeed();
        feedPresenter.setSelectedEntityReference(currentFeed);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc alertRule) {
        return alertRule.copy()
                .description(getView().getDescription())
                .languageVersion(getView().getLanguageVersion())
                .query(codePresenter.getText())
                .alertRuleType(getView().getAlertRuleType())
                .destinationFeed(feedPresenter.getSelectedEntityReference())
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

    public interface AlertRuleSettingsView extends View, HasUiHandlers<DirtyUiHandlers> {

        String getDescription();

        void setDescription(final String description);

        QueryLanguageVersion getLanguageVersion();

        void setLanguageVersion(final QueryLanguageVersion languageVersion);

        void setQueryWidget(Widget widget);

        AnalyticRuleType getAlertRuleType();

        void setAlertRuleType(AnalyticRuleType analyticRuleType);

        void setDestinationFeedView(View view);
    }
}
