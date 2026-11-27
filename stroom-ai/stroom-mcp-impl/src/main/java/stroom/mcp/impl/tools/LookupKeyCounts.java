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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.Lookup;
import stroom.ai.impl.tools.generation.LookupKeys;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Whether a translation's lookups make their keys of as many fields as the loaders of their maps do: a lookup with
/// `key_fields` of three fields finds nothing in a map whose loader keys it by one, and the reverse. The loaders are
/// the reference-data XSLTs the user can view ([ReferenceData#mapsInXslt]); a key the reference generator made of
/// several fields is read back by [LookupKeys#partCount].
class LookupKeyCounts {

    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;

    /// @param pipelineIndex  Finds the XSLTs that write reference data.
    /// @param documentAccess Reads them as the user.
    @Inject
    LookupKeyCounts(final PipelineIndex pipelineIndex, final DocumentAccess documentAccess) {
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
    }

    /// @param mapping A translation mapping.
    /// @return A warning for each lookup whose key is made of a different number of fields than a loader of its map
    /// makes it of; none when they agree, or the map isn't found.
    List<String> mismatches(final TranslationMapping mapping) {
        final Map<String, Integer> wanted = new LinkedHashMap<>();
        for (final FieldMapping entry : mapping.everyEntry()) {
            count(entry.lookup(), wanted);
            if (entry.maskNames() != null) {
                count(entry.maskNames().lookup(), wanted);
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final DocRef xslt : pipelineIndex.containing(XsltDoc.TYPE, ReferenceData.REF_NS)) {
            final Optional<XsltDoc> doc = documentAccess.read(xslt, XsltDoc.class);
            final String text = doc.map(found -> NullSafe.string(found.getData())).orElse("");
            if (!text.contains(ReferenceData.REF_NS)) {
                continue;
            }
            for (final Map<String, Object> map : ReferenceData.mapsInXslt(text)) {
                final String name = String.valueOf(map.get("map"));
                final Integer count = wanted.get(name);
                final int written = LookupKeys.partCount(String.valueOf(map.get("key")));
                if (count != null && count != written) {
                    out.add("lookup '" + name + "' makes its key of " + count + " field(s), but XSLT '"
                            + xslt.getName() + "' loads map " + name + " with a key of " + written + ": give the "
                            + "lookup's key_fields the fields the loader's key_fields name, in the same order "
                            + "(build_reference_xslt), or the lookup finds nothing");
                }
            }
        }
        return out;
    }

    private static void count(final Lookup lookup, final Map<String, Integer> wanted) {
        if (lookup != null) {
            wanted.put(lookup.map(), lookup.keyFields() == null
                    ? 1
                    : lookup.keyFields().size());
        }
    }
}
