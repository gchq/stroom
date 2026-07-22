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

package stroom.pipeline.stepping.store;

import stroom.util.shared.Indicators;

/**
 * One element's captured IO for one record, in the form the store holds it. The stored counterpart of the
 * wire {@code SharedElementData}: same fields, but {@code input}/{@code output} are {@link CapturedData}
 * (events or text) rather than serialised strings, so XML output is kept as replayable events. The wire form
 * is derived from this on read.
 * <p>
 * {@code formatInput}/{@code formatOutput} are carried explicitly rather than inferred from the data format,
 * because they do not always agree with it (e.g. an {@code XMLWriter} produces text but is marked formatted).
 */
public record CapturedElementData(CapturedData input,
                                  CapturedData output,
                                  boolean formatInput,
                                  boolean formatOutput,
                                  boolean hasOutput,
                                  Indicators indicators) {

    /**
     * The input as text if it is a text side, else null. A convenience for callers that only deal in text;
     * the wire form (which renders SAX sides too) is produced by {@link CapturedElementDataMapper}.
     */
    public String inputText() {
        return textOf(input);
    }

    public String outputText() {
        return textOf(output);
    }

    private static String textOf(final CapturedData data) {
        return data == null || data.isSaxEvents() ? null : data.asText();
    }
}
