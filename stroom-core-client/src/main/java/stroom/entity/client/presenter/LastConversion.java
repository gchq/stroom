/*
 * Copyright 2026 Crown Copyright
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

package stroom.entity.client.presenter;

import java.util.Objects;
import java.util.function.Function;

/// The last text converted and what it was converted to, so the same text converted again is not converted again.
/// A large Documentation doc's markdown takes seconds to render, and the same text is rendered more than once:
/// for its Documentation tab and for its editor's preview, and again when it is opened again.
final class LastConversion {

    private String lastText;
    private String lastConverted;

    /// @param text      The text to convert.
    /// @param converter What converts it; called only when the text is not the last text converted.
    /// @return The text converted.
    String convert(final String text, final Function<String, String> converter) {
        Objects.requireNonNull(converter);
        if (lastConverted == null || !Objects.equals(text, lastText)) {
            final String converted = converter.apply(text);
            lastText = text;
            lastConverted = converted;
        }
        return lastConverted;
    }
}
