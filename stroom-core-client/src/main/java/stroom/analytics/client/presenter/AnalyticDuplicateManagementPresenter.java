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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.DuplicateNotificationConfig;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AnalyticDuplicateManagementPresenter
        extends AbstractDuplicateManagementPresenter<AnalyticRuleDoc> {

    @Inject
    public AnalyticDuplicateManagementPresenter(final EventBus eventBus,
                                                final DuplicateManagementView view,
                                                final DuplicateManagementListPresenter
                                                        duplicateManagementListPresenter) {
        super(eventBus, view, duplicateManagementListPresenter);
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        final DuplicateNotificationConfig duplicateNotificationConfig = writeDuplicateNotificationConfig();
        return document
                .copy()
                .duplicateNotificationConfig(duplicateNotificationConfig)
                .build();
    }
}
