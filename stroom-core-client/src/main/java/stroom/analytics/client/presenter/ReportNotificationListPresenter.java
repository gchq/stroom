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

import stroom.analytics.shared.ReportDoc;
import stroom.data.grid.client.PagerView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;

public class ReportNotificationListPresenter
        extends AbstractNotificationListPresenter<ReportDoc> {

    @Inject
    public ReportNotificationListPresenter(final EventBus eventBus,
                                           final PagerView view,
                                           final Provider<AnalyticNotificationEditPresenter> editPresenterProvider) {
        super(eventBus, view, editPresenterProvider);
    }

    @Override
    protected ReportDoc onWrite(final ReportDoc document) {
        return document.copy().notifications(new ArrayList<>(list)).build();
    }
}
