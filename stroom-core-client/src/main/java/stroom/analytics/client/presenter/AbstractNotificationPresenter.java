/*
 * Copyright 2024 Crown Copyright
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

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.ReportDoc;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.client.event.ChangeDataEvent;
import stroom.pipeline.client.event.ChangeDataEvent.ChangeDataHandler;
import stroom.pipeline.client.event.HasChangeDataHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractNotificationPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocumentEditPresenter<AbstractNotificationPresenter.AnalyticNotificationView, D>
        implements DirtyUiHandlers, HasChangeDataHandlers<AnalyticProcessType> {

    final DocSelectionBoxPresenter errorFeedPresenter;
    private final AbstractNotificationListPresenter notificationList;
    private final UiConfigCache uiConfigCache;

    @Inject
    public AbstractNotificationPresenter(final EventBus eventBus,
                                         final AnalyticNotificationView view,
                                         final DocSelectionBoxPresenter errorFeedPresenter,
                                         final AbstractNotificationListPresenter notificationList,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        this.errorFeedPresenter = errorFeedPresenter;
        this.notificationList = notificationList;
        view.setUiHandlers(this);

        errorFeedPresenter.setIncludedTypes(FeedDoc.TYPE);
        errorFeedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        getView().setErrorFeedView(errorFeedPresenter.getView());
        getView().setTable(notificationList.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(errorFeedPresenter.addDataSelectionHandler(e -> onDirty()));
        registerHandler(notificationList.addDirtyHandler(event -> setDirty(true)));
    }

    @Override
    public HandlerRegistration addChangeDataHandler(final ChangeDataHandler<AnalyticProcessType> handler) {
        return addHandlerToSource(ChangeDataEvent.getType(), handler);
    }

    @Override
    protected void onRead(final DocRef docRef, final D analyticRuleDoc, final boolean readOnly) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                DocRef selectedDocRef = analyticRuleDoc.getErrorFeed();
                if (selectedDocRef == null) {
                    if (ReportDoc.TYPE.equals(docRef.getType())) {
                        selectedDocRef = extendedUiConfig.getReportUiDefaultConfig().getDefaultErrorFeed();
                    } else {
                        selectedDocRef = extendedUiConfig.getAnalyticUiDefaultConfig().getDefaultErrorFeed();
                    }
                }

                if (selectedDocRef != null) {
                    errorFeedPresenter.setSelectedEntityReference(selectedDocRef, true);
                }
                notificationList.read(docRef, analyticRuleDoc, readOnly);
                getView().setIncludeRuleDocumentation(analyticRuleDoc.isIncludeRuleDocumentation());
            }
        }, this);
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        this.notificationList.setTaskMonitorFactory(taskMonitorFactory);
    }


    // --------------------------------------------------------------------------------


    public interface AnalyticNotificationView extends View, HasUiHandlers<DirtyUiHandlers> {

        void setErrorFeedView(View view);

        void setIncludeRuleDocumentation(Boolean includeRuleDocumentation);

        Boolean isIncludeRuleDocumentation();

        void setTable(View view);
    }
}
