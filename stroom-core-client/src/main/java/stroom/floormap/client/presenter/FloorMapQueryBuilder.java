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

package stroom.floormap.client.presenter;

import stroom.floormap.client.model.FloorMapFactHistory;
import stroom.floormap.client.value.ValuePathAccessor;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.ValueFormat;
import stroom.query.api.token.QuotedStringUtil;

import java.util.List;

/// Generates StroomQL queries for reading floor map facts from a temporal store.
///
/// The generated query is derived entirely from the
/// [FloorMapFieldMapping] value schema and the [ValueFormat]. Each
/// mapping's [path][FloorMapFieldMapping#getPath()] is wrapped in the
/// appropriate extraction function (`jq()` for JSON, `xpath()` for
/// XML) and aliased to a SQL-safe column name.
///
/// This eliminates the need for a user-editable "Facts Query" — the query
/// is always in sync with the value schema configured in the Settings tab.
public final class FloorMapQueryBuilder {

    private FloorMapQueryBuilder() {
        // Utility class
    }

    /// Builds a StroomQL query string that selects `Key`,
    /// `EffectiveTime`, `toLong(EffectiveTime)`, and one column per
    /// schema mapping from the `param('FactStore')` source.
    ///
    /// The `toLong(EffectiveTime)` column carries the effective time as raw
    /// epoch millis, aliased [FloorMapFactHistory#EFFECTIVE_TIME_MS_COLUMN].
    /// `FloorMapFactHistory` derives the snapshot at a timeline position from
    /// the held history, which means comparing each row's effective time against
    /// that position — and the `EffectiveTime` column arrives as text. It is
    /// ISO-8601 today, because StroomQL sets no `Format` on select columns and
    /// so `Unformatted` calls `ValDate.toString()`, but resting the map's
    /// correctness on a formatter default two modules away is not worth the one extra
    /// column it costs to avoid.
    ///
    /// `EffectiveTime` itself is retained: it is the readable one, and the
    /// Events Query tab's results table shows these columns to the user.
    ///
    /// @param schema the value schema mappings; must not be `null` or empty
    /// @param format the value serialisation format; must not be `null`
    /// @return the generated StroomQL query string
    public static String buildFactsQuery(final List<FloorMapFieldMapping> schema,
                                         final ValueFormat format) {
        final StringBuilder sb = new StringBuilder();
        sb.append("from param('FactStore')\nselect \n  Key, \n  EffectiveTime, \n  toLong(EffectiveTime) as \"")
                .append(QuotedStringUtil.escapeDoubleQuoted(FloorMapFactHistory.EFFECTIVE_TIME_MS_COLUMN))
                .append("\"");

        for (final FloorMapFieldMapping mapping : schema) {
            final String path = mapping.getPath();
            if (path == null || path.isEmpty()) {
                continue;
            }
            final String expr = buildExtractExpression(path, format);
            final String alias = buildColumnAlias(path, format);
            // Quoted at the point of emission, not inside buildColumnAlias: the alias is
            // also the name the results are matched back by, and the server reports it
            // unescaped, so the two must agree on the bare form. StroomQL accepts a quoted
            // string after AS (TokenType.ALL_STRINGS includes DOUBLE_QUOTED_STRING) and
            // takes the column name from its unescaped text.
            sb.append(", \n  ").append(expr).append(" as \"")
                    .append(QuotedStringUtil.escapeDoubleQuoted(alias)).append("\"");
        }
        return sb.toString();
    }

    /// The parameter the histogram query reads its bucket width from.
    ///
    /// A parameter rather than text substituted into the query, because the width is chosen from
    /// the visible range and so changes on every zoom. Substituting it would mean rewriting the
    /// user's own query text on every range change; passing it leaves the text alone.
    public static final String PARAM_BUCKET_WIDTH = "bucketWidth";

    /// The default query behind the timeline's density histogram.
    ///
    /// A starting point rather than a fixed query: it is written to the document on creation and
    /// the user may then edit it — to count only some event types, say. What must survive editing is
    /// the shape: one row per bucket, so the answer is bounded by the range rather than by how many
    /// events the store holds.
    ///
    /// The bucket width arrives as [#PARAM_BUCKET_WIDTH]; `floorTime` parses its second
    /// argument at evaluation, so a parameter works there exactly as a literal would.
    public static String defaultHistogramQuery() {
        return "from param('EventStore')\n"
               + "eval bucket = floorTime(EffectiveTime, param('" + PARAM_BUCKET_WIDTH + "'))\n"
               + "group by bucket\n"
               + "sort by bucket\n"
               + "select bucket, count()";
    }

    /// The default query behind the timeline's "Show All" extent.
    ///
    /// Two values in one row — the first and last event times — which is what the extent actually
    /// is. An earlier version grouped into day buckets and took the first and last, which cost ~365
    /// rows a year and could only place "Show All" on the right day rather than the right instant.
    /// `min` and `max` are aggregate functions whose calculator returns the input value,
    /// so a date column stays a date.
    ///
    /// **The constant group is load-bearing.** Aggregates with no `group by` do not
    /// collapse to a single row in StroomQL — every row becomes its own group, so `min` and
    /// `max` each return that row's own time and the extent comes back as a zero-width range.
    /// Grouping by a constant makes the whole store one group. The grouped column must also be
    /// *selected*: `group by` alone, without the column in the `select` list, does
    /// not aggregate either. Both were found by running the query against a real store — the design
    /// note had recorded the single-row assumption as unverified, and it was wrong.
    ///
    /// **Deliberately unbounded**, which is what separates it from the histogram: the bars are
    /// bounded below at the visible range, so an extent taken from them could never reach data
    /// earlier than what is already shown — the one thing "Show All" exists to do.
    ///
    /// A `where` clause added here narrows what "Show All" fits to, which is worth saying in
    /// the editor because it does not look like a bound.
    public static String defaultExtentQuery() {
        //noinspection TextBlockMigration
        return "from param('EventStore')\n"
               + "eval allRows = 1\n"
               + "group by allRows\n"
               + "select allRows, min(EffectiveTime), max(EffectiveTime)";
    }

    /// Builds the StroomQL extraction expression for a single path.
    ///
    /// For JSON, wraps in `jq(Value, ...)`. Keys containing
    /// characters that are not valid unquoted jq identifiers (e.g.
    /// hyphens) are quoted.
    ///
    /// For XML, wraps in `xpath(Value, ...)` using the path
    /// directly (which is expected to be a valid XPath from the root
    /// of the Value XML).
    ///
    /// @param path   the schema path (e.g. `".type"` for JSON,
    ///         `"/entry/type"` for XML)
    /// @param format the value serialisation format
    /// @return the StroomQL expression (e.g. `jq(Value, ".type")`)
    public static String buildExtractExpression(final String path,
                                                final ValueFormat format) {
        return switch (format) {
            case JSON -> {
                final String key = ValuePathAccessor.toKey(path);
                // Build the jq expression first, then escape it once for the
                // enclosing StroomQL literal. Doing both levels inline produced
                // backslash soup and, more importantly, escaped neither: a path
                // containing a quote closed the StroomQL literal early.
                final String jq = needsQuoting(key)
                        // Field access on a quoted key needs the leading dot:
                        // ."tm-world-to-map". Without it the jq expression is just
                        // a string literal that evaluates to itself.
                        ? "." + '"' + QuotedStringUtil.escapeDoubleQuoted(key) + '"'
                        : path;
                yield "jq(Value, \"" + QuotedStringUtil.escapeDoubleQuoted(jq) + "\")";
            }
            case XML -> "xpath(Value, \""
                    + QuotedStringUtil.escapeDoubleQuoted(path) + "\")";
        };
    }

    /// Derives the column alias for a schema path.
    ///
    /// This is the **bare** name, not quoted. It is used for two things that
    /// must agree: [#buildFactsQuery] emits it after `AS` (quoting it there),
    /// and the results are matched back to their roles by comparing it against the column
    /// names the server reports — which are the unescaped form. Quoting here would break
    /// that comparison.
    ///
    /// **The mapping is injective:** two different paths always give two
    /// different aliases. That matters more than a tidy name, because the consumer looks a
    /// column up *by* alias, so two paths sharing one alias do not merely produce an
    /// odd heading — they make two roles resolve to the same column, and the map silently
    /// draws with the wrong data. Two previous behaviours broke injectivity:
    ///
    /// - JSON replaced hyphens with underscores, so `.a-b` and `.a_b` both
    ///   became `a_b`. The key is now used verbatim; a heading reads
    ///   `tm-world-to-map` rather than `tm_world_to_map`, which is also
    ///   closer to what the field is actually called.
    /// - XML took only the last segment, so `/entry/type` and
    ///   `/entry/meta/type` both became `type`. The path is now used with
    ///   only its leading `/` removed.
    ///
    /// The XML change costs some brevity in the results table — a heading reads
    /// `entry/type` rather than `type`. A friendlier scheme would have to
    /// disambiguate duplicates across the whole schema, and the obvious way to do that
    /// (suffixing `_2`) makes the alias depend on schema *order*, so
    /// reordering rows in the Settings grid would silently reassign columns. A stable,
    /// order-independent alias is worth a longer heading.
    ///
    /// Special characters need no escaping here: whatever the path contains, the alias is
    /// quoted where it is emitted.
    ///
    /// @param path   the schema path
    /// @param format the value serialisation format
    /// @return the bare column alias; quote it before putting it in query text
    public static String buildColumnAlias(final String path,
                                          final ValueFormat format) {
        return switch (format) {
            case JSON -> ValuePathAccessor.toKey(path);
            case XML -> path.startsWith("/")
                    ? path.substring(1)
                    : path;
        };
    }

    /// Returns `true` if the key contains characters that require
    /// quoting in a jq expression (anything other than alphanumerics
    /// and underscores).
    private static boolean needsQuoting(final String key) {
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return true;
            }
        }
        return false;
    }
}
