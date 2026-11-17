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

import stroom.ai.impl.tools.indexing.IndexNamePatterns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// An Elasticsearch indexing pipeline's index name with the variables filled in whose values are known before it
/// runs. Stroom's ElasticIndexingFilter fills `{name}` per document from the document's root field `name`, as
/// written (no case change), so `{feed}` is not the feed's name of itself: it is whatever the indexing XSLT writes
/// as `feed`. Its value is known only where the XSLT writes the field as a fixed text, or as the stream's feed name
/// (`stroom:feed-name()`, perhaps lower- or upper-cased), and then once for each feed the pipeline processes. Any
/// other variable (a value read from the event, or a field written in more than one way) stays a variable, taken as
/// any value. Seen in review: two production children of a `{feed}-events` template, for different feeds, compared
/// as the same unresolved name, so neither told the feeds apart.
final class IndexNameValues {

    // <string key="feed">...</string>, any prefix: a field the XSLT writes in Stroom's JSON XML.
    private static final Pattern STRING_FIELD = Pattern.compile(
            "<(?:[\\w.-]+:)?string\\s+key\\s*=\\s*([\"'])([^\"']*)\\1\\s*>(.*?)</(?:[\\w.-]+:)?string\\s*>",
            Pattern.DOTALL);
    private static final Pattern VALUE_OF = Pattern.compile(
            "^<xsl:value-of\\s+select\\s*=\\s*(\"([^\"]*)\"|'([^']*)')\\s*/>$");
    private static final Pattern TEXT = Pattern.compile("^<xsl:text>([^<]*)</xsl:text>$");
    // stroom:feed-name(), perhaps in lower-case() or upper-case().
    private static final Pattern FEED_NAME = Pattern.compile(
            "^(?:(lower-case|upper-case)\\((?:[\\w.-]+:)?feed-name\\(\\)\\)|(?:[\\w.-]+:)?feed-name\\(\\))$");
    private static final Pattern QUOTED = Pattern.compile("^(?:'([^']*)'|\"([^\"]*)\")$");

    private IndexNameValues() {
        // Static only.
    }

    /// @param indexName An index name, perhaps with variables, e.g. `{feed}-events`.
    /// @param xslts     The code of the XSLTs the pipeline runs (its indexing XSLT among them).
    /// @param feeds     The feeds whose streams it processes.
    /// @return The names it writes to: one a feed when a variable is the feed's name, the variables whose values
    /// aren't known kept as they are (each taken as any value); the name itself when it has no variables.
    static List<String> filled(final String indexName,
                               final List<String> xslts,
                               final Collection<String> feeds) {
        final Set<String> variables = IndexNamePatterns.variables(indexName);
        if (variables.isEmpty()) {
            return List.of(indexName);
        }
        final Map<String, Fill> fills = new LinkedHashMap<>();
        for (final String variable : variables) {
            fill(xslts, variable).ifPresent(fill -> fills.put(variable, fill));
        }
        final boolean byFeed = fills.values().stream().anyMatch(fill -> fill.feedCase() != null);
        final List<String> perFeed = byFeed && !feeds.isEmpty()
                ? List.copyOf(new TreeSet<>(feeds))
                : null;
        final Set<String> names = new LinkedHashSet<>();
        if (perFeed == null) {
            names.add(substitute(indexName, fills, null));
        } else {
            perFeed.forEach(feed -> names.add(substitute(indexName, fills, feed)));
        }
        return List.copyOf(names);
    }

    /// @param xslts    The code of a pipeline's XSLTs.
    /// @param variable A variable of its index name, e.g. `feed`.
    /// @return How the XSLTs write the field of that name, when every place writes it the same known way: a fixed
    /// text, or the feed's name; empty otherwise.
    static Optional<Fill> fill(final List<String> xslts, final String variable) {
        final List<Fill> found = new ArrayList<>();
        for (final String xslt : xslts) {
            if (xslt == null) {
                continue;
            }
            final Matcher matcher = STRING_FIELD.matcher(xslt);
            while (matcher.find()) {
                if (!variable.equals(matcher.group(2))) {
                    continue;
                }
                final Optional<Fill> fill = read(matcher.group(3).strip());
                if (fill.isEmpty()) {
                    // Written from something else (the event's own values): any value.
                    return Optional.empty();
                }
                found.add(fill.get());
            }
        }
        if (found.isEmpty() || found.stream().distinct().count() > 1) {
            return Optional.empty();
        }
        return Optional.of(found.getFirst());
    }

    // A field's content: a fixed text, or the feed's name, as Stroom's filter reads it.
    private static Optional<Fill> read(final String content) {
        if (!content.contains("<")) {
            // An empty value is never used (Stroom then fails the document): no value known.
            return content.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Fill(content, null));
        }
        final Matcher text = TEXT.matcher(content);
        if (text.matches()) {
            return text.group(1).isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Fill(text.group(1), null));
        }
        final Matcher valueOf = VALUE_OF.matcher(content);
        if (!valueOf.matches()) {
            return Optional.empty();
        }
        final String select = (valueOf.group(2) != null
                ? valueOf.group(2)
                : valueOf.group(3)).replaceAll("\\s+", "");
        final Matcher feed = FEED_NAME.matcher(select);
        if (feed.matches()) {
            return Optional.of(new Fill(null, feed.group(1) == null
                    ? FeedCase.AS_IS
                    : "lower-case".equals(feed.group(1))
                            ? FeedCase.LOWER
                            : FeedCase.UPPER));
        }
        final Matcher quoted = QUOTED.matcher(select);
        if (quoted.matches()) {
            final String literal = quoted.group(1) != null
                    ? quoted.group(1)
                    : quoted.group(2);
            return literal.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Fill(literal, null));
        }
        return Optional.empty();
    }

    private static String substitute(final String indexName, final Map<String, Fill> fills, final String feed) {
        return IndexNamePatterns.VARIABLE.matcher(indexName).replaceAll(match -> {
            final Fill fill = fills.get(match.group(1));
            if (fill == null || (fill.feedCase() != null && feed == null)) {
                return Matcher.quoteReplacement(match.group());
            }
            return Matcher.quoteReplacement(fill.value(feed));
        });
    }


    // --------------------------------------------------------------------------------


    /// How the feed's name is written.
    enum FeedCase {
        AS_IS,
        LOWER,
        UPPER
    }


    // --------------------------------------------------------------------------------


    /// How an XSLT writes a variable's field.
    ///
    /// @param literal  The fixed text written, or null.
    /// @param feedCase For the feed's name, how it is cased; or null.
    record Fill(String literal, FeedCase feedCase) {

        /// @param feed The feed processed.
        /// @return The value written for it.
        String value(final String feed) {
            if (feedCase == null) {
                return literal;
            }
            return switch (feedCase) {
                case AS_IS -> feed;
                case LOWER -> feed.toLowerCase(Locale.ROOT);
                case UPPER -> feed.toUpperCase(Locale.ROOT);
            };
        }
    }
}
