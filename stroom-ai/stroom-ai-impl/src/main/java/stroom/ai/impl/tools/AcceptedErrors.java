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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Errors a user has accepted as benign for a pipeline, kept in the pipeline's Documentation doc.
///
/// When the user says an error is benign and can be ignored (a lookup miss for accounts the reference data does not
/// hold, say), the agent records it with the user's reason; triage then classes that kind of error as benign for the
/// pipeline, saying why, so later reviews do not raise it again. An entry matches by element and by message with its
/// variable parts (quoted values, numbers) masked, as triage groups errors.
public class AcceptedErrors {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AcceptedErrors.class);

    private static final Pattern BLOCK = Pattern.compile("<!-- stroom-mcp accepted errors\n(.*?)\n-->",
            Pattern.DOTALL);

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;

    /// @param docFinderProvider Finds Documentation docs by name.
    /// @param documentAccess    Reads them, as the user.
    @Inject
    public AcceptedErrors(final Provider<DocFinder> docFinderProvider, final DocumentAccess documentAccess) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
    }

    /// Every accepted error recorded for a pipeline: in the Documentation docs named after it that the user can
    /// view. Accepted errors are a courtesy: never a reason for triage to fail, so anything that goes wrong gives
    /// none.
    ///
    /// @param pipelineUuid The pipeline.
    /// @return The entries, newer replacing older for the same element and pattern.
    public List<Entry> acceptedFor(final String pipelineUuid) {
        try {
            final String name = documentAccess.findDocRef(pipelineUuid)
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                    .map(DocRef::getName)
                    .orElse(null);
            if (NullSafe.isBlankString(name)) {
                return List.of();
            }
            List<Entry> entries = new ArrayList<>();
            for (final DocRef docRef : docFinderProvider.get().findByName(DocumentationDoc.TYPE, name)) {
                if (name.equals(docRef.getName())) {
                    final String text = documentAccess.read(docRef, DocumentationDoc.class)
                            .map(DocumentationDoc::getData)
                            .orElse(null);
                    entries = merge(entries, readAccepted(text));
                }
            }
            return entries;
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the accepted errors of pipeline {}", pipelineUuid, e);
            return List.of();
        }
    }

    /// @param markdown A Documentation doc's text, or null.
    /// @return The accepted errors recorded in it.
    public static List<Entry> readAccepted(final String markdown) {
        final Matcher matcher = BLOCK.matcher(NullSafe.string(markdown));
        if (!matcher.find()) {
            return List.of();
        }
        final List<?> items;
        try {
            items = JsonUtil.readValue(matcher.group(1), List.class);
        } catch (final RuntimeException e) {
            return List.of();
        }
        final List<Entry> entries = new ArrayList<>();
        for (final Object item : NullSafe.list(items)) {
            if (item instanceof final Map<?, ?> map && map.get("pattern") != null) {
                entries.add(new Entry(string(map.get("element")), String.valueOf(map.get("pattern")),
                        string(map.get("example")), string(map.get("reason")), string(map.get("accepted"))));
            }
        }
        return entries;
    }

    /// @param element The element the error is of, or null for any element.
    /// @param example The error, as it was seen.
    /// @param reason  Why the user accepted it.
    /// @param date    When.
    /// @param matches The kind of message covered, with `*` for the parts that vary (e.g. `No HR record for user
    ///                svc-*`); null for the example, with its numbers and quoted values varying.
    /// @return The entry.
    public static Entry entry(final String element,
                              final String example,
                              final String reason,
                              final String date,
                              final String matches) {
        final String exampleText = NullSafe.string(example);
        return new Entry(NullSafe.isBlankString(element)
                ? null
                : element,
                ErrorTriage.normalise(Objects.requireNonNullElse(matches, exampleText)),
                exampleText.length() > 300
                        ? exampleText.substring(0, 300)
                        : exampleText,
                reason,
                date);
    }

    /// @param entries The entries.
    /// @return Them as kept in a Documentation doc's text, or an empty string if there are none.
    public static String block(final List<Entry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        final List<Map<String, Object>> maps = entries.stream().map(Entry::toMap).toList();
        return "<!-- stroom-mcp accepted errors\n" + JsonUtil.writeValueAsString(maps, true) + "\n-->";
    }

    /// @return The entries, new ones replacing old ones for the same element and pattern.
    public static List<Entry> merge(final List<Entry> old, final List<Entry> added) {
        final Map<List<String>, Entry> keyed = new LinkedHashMap<>();
        for (final Entry entry : old) {
            keyed.put(entry.key(), entry);
        }
        for (final Entry entry : added) {
            keyed.put(entry.key(), entry);
        }
        return List.copyOf(keyed.values());
    }

    /// @return The accepted entry an error falls under, or null if none.
    public static Entry match(final List<Entry> entries, final String element, final String message) {
        final String seen = ErrorTriage.normalise(message);
        for (final Entry entry : NullSafe.list(entries)) {
            if (entry.element() != null && !entry.element().equals(element)) {
                continue;
            }
            // A pattern with * covers messages containing it (Stroom prefixes some, e.g. 'Log - ').
            final boolean matches = entry.pattern().contains("*")
                    ? wildcard("*" + entry.pattern() + "*").matcher(seen).matches()
                    : entry.pattern().equals(seen);
            if (matches) {
                return entry;
            }
        }
        return null;
    }

    private static Pattern wildcard(final String pattern) {
        final StringBuilder regex = new StringBuilder();
        for (final String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static String string(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// One accepted error.
    ///
    /// @param element  The element it is of, or null for any element.
    /// @param pattern  The message, normalised as triage groups them, with `*` for parts that vary.
    /// @param example  The error, as it was seen.
    /// @param reason   Why the user accepted it.
    /// @param accepted When.
    public record Entry(String element, String pattern, String example, String reason, String accepted) {

        private List<String> key() {
            return Arrays.asList(element, pattern);
        }

        /// @return The entry as JSON keeps it.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("element", element);
            map.put("pattern", pattern);
            map.put("example", example);
            map.put("reason", reason);
            map.put("accepted", accepted);
            return map;
        }
    }
}
