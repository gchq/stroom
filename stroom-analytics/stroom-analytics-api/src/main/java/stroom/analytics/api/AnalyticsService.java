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

package stroom.analytics.api;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.util.shared.Message;

import java.util.List;

public interface AnalyticsService {

    String testTemplate(final String template);

    void sendTestEmail(final NotificationEmailDestination emailDestination);

    /**
     * Compares analytic to the currently persisted version and validates those changes.
     *
     * @return A list of validation messages.
     */
    List<Message> validateChanges(final AnalyticRuleDoc analytic);
}
