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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.ToolText;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/// A shared XSLT whose xsl:functions the mapping's xpaths call, as the environment's other XSLTs call them.
///
/// @param href      The shared XSLT document, as the other pipelines import it, e.g. `IP Lookup`.
/// @param prefix    The prefix its functions are called with, e.g. `gs` for `gs:isLocalIpAddress()`.
/// @param namespace The namespace that prefix is bound to in the shared XSLT.
public record SharedFunctions(String href,
                              String prefix,
                              String namespace) {

    private static final Pattern NCNAME = Pattern.compile("[A-Za-z_][\\w.-]*", Pattern.UNICODE_CHARACTER_CLASS);

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static SharedFunctions read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "SharedFunctions");
        if (fields == null) {
            return null;
        }
        final String href = fields.requiredString("href");
        final String prefix = fields.requiredString("prefix");
        if (prefix != null && (!NCNAME.matcher(prefix).matches()
                               || prefix.toLowerCase(Locale.ROOT).startsWith("xml"))) {
            reader.valueError(ModelReader.at(loc, "prefix"), "prefix " + ToolText.repr(prefix) + " is not a "
                                                             + "namespace prefix (letters, digits, _ . -; not "
                                                             + "xml...)", prefix);
        }
        final SharedFunctions functions = new SharedFunctions(href, prefix, fields.requiredString("namespace"));
        return fields.ok()
                ? functions
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("href", href, null)
                .put("prefix", prefix, null)
                .put("namespace", namespace, null)
                .build();
    }
}
