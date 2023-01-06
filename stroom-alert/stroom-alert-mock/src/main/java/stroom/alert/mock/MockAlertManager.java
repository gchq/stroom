/*
 * Copyright 2021 Crown Copyright
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

package stroom.alert.mock;

import stroom.alert.api.AlertManager;
import stroom.alert.api.AlertProcessor;
import stroom.docref.DocRef;

import java.util.Optional;

public class MockAlertManager implements AlertManager {

    @Override
    public String getTimeZoneId() {
        return "UTC";
    }

    @Override
    public Optional<AlertProcessor> createAlertProcessor(final DocRef indexDocRef) {
        return Optional.empty();
    }

    @Override
    public String getAdditionalFieldsPrefix() {
        return "-";
    }

    @Override
    public boolean isReportAllExtractedFieldsEnabled() {
        return false;
    }

    @Override
    public void refreshRules() {

    }
}
