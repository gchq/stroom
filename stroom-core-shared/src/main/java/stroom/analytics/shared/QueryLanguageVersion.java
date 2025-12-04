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

package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

public enum QueryLanguageVersion implements HasDisplayValue {
    STROOM_QL_VERSION_0_1("Stroom Query Language v0.1"),
    SIGMA("Sigma");

    private final String displayValue;

    QueryLanguageVersion(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
