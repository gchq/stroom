/*
 * Copyright 2020 Crown Copyright
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
package stroom.alert.api;

import stroom.docref.DocRef;

import java.util.Optional;

public interface AlertManager {
    String DASHBOARD_NAME_KEY = "alertDashboardName";
    String RULES_FOLDER_KEY = "alertRulesFolder";
    String TABLE_NAME_KEY = "alertTableName";
    String DETECT_TIME_DATA_ELEMENT_NAME_ATTR = "alertDetectTime";

    String getTimeZoneId();

    Optional<AlertProcessor> createAlertProcessor(final DocRef indexDocRef);
}
