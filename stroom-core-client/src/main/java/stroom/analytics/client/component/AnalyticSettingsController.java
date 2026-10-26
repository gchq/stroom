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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// An analytic rule's Settings tab ([AnalyticSettings]), with its error feed ([ErrorFeedController]).
public class AnalyticSettingsController extends Controller {

    private final AnalyticSettings view;
    private final ErrorFeedController errorFeed;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The tab.
    /// @param errorFeedProvider Makes the error feed's controller.
    @Inject
    public AnalyticSettingsController(final EventBus eventBus,
                                      final AnalyticSettings view,
                                      final Provider<ErrorFeedController> errorFeedProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        errorFeed = make(errorFeedProvider).attach(view.getErrorFeedBox());
        view.setSetDefaultVisible(errorFeed.isSetDefaultAllowed());
        view.setOnSetDefault(errorFeed::setDefault);
        view.setOnChange(this::markChanged);
        own(errorFeed.addSelectionListener(e -> markChanged()));
        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            errorFeed.setReadOnly(readOnly);
        });
    }

    /// Shows a rule's settings.
    ///
    /// @param docRef The rule.
    /// @param rule   Its settings.
    public void read(final DocRef docRef, final AnalyticRuleDoc rule) {
        errorFeed.read(docRef, rule.getErrorFeed());
        view.read(rule);
    }

    /// Writes the settings shown into a copy of a rule.
    ///
    /// @param rule The rule.
    /// @return A copy of it with the settings shown.
    public AnalyticRuleDoc write(final AnalyticRuleDoc rule) {
        return view.write(rule.copy())
                .errorFeed(errorFeed.getErrorFeed())
                .build();
    }
}
