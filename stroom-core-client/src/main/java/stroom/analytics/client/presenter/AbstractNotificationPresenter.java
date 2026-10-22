/*
 * Copyright 2025 Crown Copyright
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

import stroom.analytics.client.presenter.AbstractNotificationPresenter.AnalyticNotificationView;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.presenter.client.View;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

public abstract class AbstractNotificationPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocPresenter<AnalyticNotificationView, D> {

    private final AbstractNotificationListPresenter<D> notificationList;

    AbstractNotificationPresenter(final EventBus eventBus,
                                  final AnalyticNotificationView view,
                                  final AbstractNotificationListPresenter<D> notificationList) {
        super(eventBus, view);
        own(notificationList);
        this.notificationList = notificationList;
        getView().setTable(notificationList.getView());
    }

    /**
     * @see AbstractNotificationListPresenter#setAnalyticProcessType(AnalyticProcessType)
     */
    public void setAnalyticProcessType(final AnalyticProcessType analyticProcessType) {
        notificationList.setAnalyticProcessType(analyticProcessType);
    }

    @Override
    protected void onRead(final DocRef docRef, final D analyticRuleDoc) {
        notificationList.read(docRef, analyticRuleDoc);
    }

    // --------------------------------------------------------------------------------


    public interface AnalyticNotificationView extends View {

        void setTable(View view);
    }
}
