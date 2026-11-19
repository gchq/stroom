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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// An analytic rule's Duplicate Management tab ([DuplicateManagement]): its duplicate notification
/// settings, and the duplicates it has remembered. Read only as its document is.
public class DuplicateManagementController extends Controller {

    private final DuplicateManagement view;
    private final DuplicateManagementListController duplicateManagementList;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                The application's event bus.
    /// @param view                    The tab.
    /// @param duplicateManagementList The remembered duplicates.
    @Inject
    public DuplicateManagementController(final EventBus eventBus,
                                         final DuplicateManagement view,
                                         final DuplicateManagementListController duplicateManagementList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.duplicateManagementList = own(duplicateManagementList).attach(view.getList());
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a rule's duplicate notification settings and remembered duplicates.
    ///
    /// @param docRef   The rule.
    /// @param document The rule.
    public void read(final DocRef docRef, final AnalyticRuleDoc document) {
        view.read(document.getDuplicateNotificationConfig());
        duplicateManagementList.read(docRef);
    }

    /// Writes the settings shown into a copy of a rule.
    ///
    /// @param document The rule.
    /// @return A copy of it with the settings shown.
    public AnalyticRuleDoc write(final AnalyticRuleDoc document) {
        return document
                .copy()
                .duplicateNotificationConfig(view.write())
                .build();
    }
}
