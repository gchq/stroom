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

package stroom.index.shared;

import stroom.docref.HasDisplayValue;

public enum LuceneVersion implements HasDisplayValue {
    LUCENE_4_6_0("LUCENE_46"),
    LUCENE_5_5_3("5.5.3"),
    LUCENE_9_8_0("9.8.0"),
    LUCENE_10_3_1("10.3.1");

    private final String displayValue;

    LuceneVersion(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
