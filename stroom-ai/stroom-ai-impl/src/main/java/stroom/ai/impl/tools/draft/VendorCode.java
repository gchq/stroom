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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.sample.JsonFieldNames;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// The vendor's code an event type's records carry: every `equals`/`one_of` field of a way of recognising the type
/// (not a `not`), except the build's source type field, written as Data of the action element named after the
/// field's last segment, so searching by the vendor's code still works. One definition, for the draft that writes it
/// ([CatalogueTranslation]) and the build check that it is still written.
public final class VendorCode {

    private VendorCode() {
        // Static only.
    }

    /// @param way       A way of recognising an event type.
    /// @param catalogue The catalogue it is of (its scope names the build's source type field), or null.
    /// @return The fields the way tests for a code, in order, each once.
    public static List<String> fields(final Way way, final Catalogue catalogue) {
        final String sourceTypeField = catalogue == null || catalogue.scope() == null
                ? null
                : catalogue.scope().sourceTypeField();
        final Set<String> out = new LinkedHashSet<>();
        for (final Condition condition : way.when()) {
            if (condition.field() != null && !condition.not() && condition.xpath() == null
                && (condition.equalTo() != null || condition.oneOf() != null)
                && !isSourceTypeField(condition.field(), sourceTypeField)) {
                out.add(condition.field());
            }
        }
        return new ArrayList<>(out);
    }

    /// @param fieldName A field's name, as conditions name it.
    /// @param jsonKeys  Whether it is a key of JSON (the JSON input's, or JSON a field holds): its dots separate keys.
    /// @return The Data name the vendor's code in it is written under: its last segment (`System/EventID` is
    /// `EventID`, `event.logger_name` is `logger_name`, `@id` is `id`; a key in brackets as written). A Data
    /// Splitter's field is named as it is.
    public static String dataName(final String fieldName, final boolean jsonKeys) {
        String last = fieldName;
        final int slash = last.lastIndexOf('/');
        if (slash >= 0 && !last.contains("[")) {
            last = last.substring(slash + 1);
        } else if (jsonKeys) {
            final List<String> keys = JsonFieldNames.keys(last);
            if (!keys.isEmpty()) {
                last = keys.getLast();
            }
        }
        return last.startsWith("@")
                ? last.substring(1)
                : last;
    }

    // The build's source type field, as its reading names it (`event>log_type`) or as a field (`event.log_type`).
    private static boolean isSourceTypeField(final String fieldName, final String reading) {
        if (reading == null || reading.isBlank()) {
            return false;
        }
        return fieldName.equals(reading) || fieldName.equals(reading.replace('>', '.'))
               || fieldName.equals(reading.replace('>', '/'));
    }
}
