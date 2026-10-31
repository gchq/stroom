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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A mapping read back from an XSLT by [XsltReader#rebuild]: the mapping (null if what was read doesn't validate), and
/// how it was come by: entries kept from the mapping kept before, new ones, ones no longer written, ones kept as the
/// expression itself, and what wasn't read.
public final class RebuiltMapping {

    private TranslationMapping mapping;
    private int reused;
    private final List<String> added = new ArrayList<>();
    private final List<String> removed = new ArrayList<>();
    private final List<String> raw = new ArrayList<>();
    private final List<String> importedCalls = new ArrayList<>();
    private final List<String> notes;
    private final List<String> lost = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    /// @param notes What the reading noted, carried over.
    RebuiltMapping(final List<String> notes) {
        this.notes = new ArrayList<>(notes);
    }

    /// @return The mapping read back, or null if it doesn't validate (a problem says why).
    public TranslationMapping mapping() {
        return mapping;
    }

    /// @return How many entries were kept from the mapping kept before, exactly as they were.
    public int reused() {
        return reused;
    }

    /// @return The entries that are new (`rule logon: EventDetail/Authenticate/Data Data host_seen`).
    public List<String> added() {
        return added;
    }

    /// @return The entries (and rules) the kept mapping had that the XSLT no longer writes.
    public List<String> removed() {
        return removed;
    }

    /// @return The entries kept as the expression itself (an xpath entry): not read as an idiom.
    public List<String> raw() {
        return raw;
    }

    /// @return The xpath entries that call an imported XSLT's functions.
    public List<String> importedCalls() {
        return importedCalls;
    }

    /// @return What wasn't read.
    public List<String> notes() {
        return notes;
    }

    /// @return What in the rules' events wasn't read, so the mapping leaves out (among [#notes()]): an XSLT
    /// construct the reader doesn't know (xsl:iterate, say), or a template it calls that isn't there.
    public List<String> lost() {
        return lost;
    }

    /// @return What stops the mapping being used.
    public List<String> problems() {
        return problems;
    }

    /// @param mapping The mapping read back.
    void setMapping(final TranslationMapping mapping) {
        this.mapping = mapping;
    }

    /// Counts one more entry kept from the kept mapping.
    void reusedOne() {
        reused++;
    }

    /// @return What the rebuild found, as rebuild_mapping reports it: entries_kept, then
    /// entries_new, entries_removed, kept_as_xpath, calls_imported_functions, not_read and problems where they
    /// aren't empty.
    public Map<String, Object> summary() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries_kept", reused);
        putIfAny(out, "entries_new", added);
        putIfAny(out, "entries_removed", removed);
        putIfAny(out, "kept_as_xpath", raw);
        putIfAny(out, "calls_imported_functions", importedCalls);
        putIfAny(out, "not_read", notes);
        putIfAny(out, "problems", problems);
        return out;
    }

    private static void putIfAny(final Map<String, Object> out, final String key, final List<String> values) {
        if (!values.isEmpty()) {
            out.put(key, values);
        }
    }
}
