/*
 * Copyright 2016-2026 Crown Copyright
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

/**
 * Generates StroomQL queries for reading floor map facts from a temporal store.
 *
 * <p>The generated query is derived entirely from the
 * {@link FloorMapFieldMapping} value schema and the {@link ValueFormat}. Each
 * mapping's {@link FloorMapFieldMapping#getPath() path} is wrapped in the
 * appropriate extraction function ({@code jq()} for JSON, {@code xpath()} for
 * XML) and aliased to a SQL-safe column name.</p>
 *
 * <p>This eliminates the need for a user-editable "Facts Query" — the query
 * is always in sync with the value schema configured in the Settings tab.</p>
 */
public final class FloorMapQueryBuilder {

    private FloorMapQueryBuilder() {
        // Utility class
    }

    /**
     * Builds a StroomQL query string that selects {@code Key},
     * {@code EffectiveTime}, {@code toLong(EffectiveTime)}, and one column per
     * schema mapping from the {@code param('FactStore')} source.
     *
     * <p>The {@code toLong(EffectiveTime)} column carries the effective time as raw
     * epoch millis, aliased {@link FloorMapFactHistory#EFFECTIVE_TIME_MS_COLUMN}.
     * {@code FloorMapFactHistory} derives the snapshot at a timeline position from
     * the held history, which means comparing each row's effective time against
     * that position — and the {@code EffectiveTime} column arrives as text. It is
     * ISO-8601 today, because StroomQL sets no {@code Format} on select columns and
     * so {@code Unformatted} calls {@code ValDate.toString()}, but resting the map's
     * correctness on a formatter default two modules away is not worth the one extra
     * column it costs to avoid.</p>
     *
     * <p>{@code EffectiveTime} itself is retained: it is the readable one, and the
     * Events Query tab's results table shows these columns to the user.</p>
     *
     * @param schema the value schema mappings; must not be {@code null} or empty
     * @param format the value serialisation format; must not be {@code null}
     * @return the generated StroomQL query string
     */
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

    /**
     * The parameter the histogram query reads its bucket width from.
     *
     * <p>A parameter rather than text substituted into the query, because the width is chosen from
     * the visible range and so changes on every zoom. Substituting it would mean rewriting the
     * user's own query text on every range change; passing it leaves the text alone.</p>
     */
    public static final String PARAM_BUCKET_WIDTH = "bucketWidth";

    /**
     * The default query behind the timeline's density histogram.
     *
     * <p>A starting point rather than a fixed query: it is written to the document on creation and
     * the user may then edit it — to count only some event types, say. What must survive editing is
     * the shape: one row per bucket, so the answer is bounded by the range rather than by how many
     * events the store holds.</p>
     *
     * <p>The bucket width arrives as {@link #PARAM_BUCKET_WIDTH}; {@code floorTime} parses its second
     * argument at evaluation, so a parameter works there exactly as a literal would.</p>
     */
    public static String defaultHistogramQuery() {
        return "from param('EventStore')\n"
               + "eval bucket = floorTime(EffectiveTime, param('" + PARAM_BUCKET_WIDTH + "'))\n"
               + "group by bucket\n"
               + "sort by bucket\n"
               + "select bucket, count()";
    }

    /**
     * The default query behind the timeline's "Show All" extent.
     *
     * <p>Two values in one row — the first and last event times — which is what the extent actually
     * is. An earlier version grouped into day buckets and took the first and last, which cost ~365
     * rows a year and could only place "Show All" on the right day rather than the right instant.
     * {@code min} and {@code max} are aggregate functions whose calculator returns the input value,
     * so a date column stays a date.</p>
     *
     * <p><b>The constant group is load-bearing.</b> Aggregates with no {@code group by} do not
     * collapse to a single row in StroomQL — every row becomes its own group, so {@code min} and
     * {@code max} each return that row's own time and the extent comes back as a zero-width range.
     * Grouping by a constant makes the whole store one group. The grouped column must also be
     * <em>selected</em>: {@code group by} alone, without the column in the {@code select} list, does
     * not aggregate either. Both were found by running the query against a real store — the design
     * note had recorded the single-row assumption as unverified, and it was wrong.</p>
     *
     * <p><b>Deliberately unbounded</b>, which is what separates it from the histogram: the bars are
     * bounded below at the visible range, so an extent taken from them could never reach data
     * earlier than what is already shown — the one thing "Show All" exists to do.</p>
     *
     * <p>A {@code where} clause added here narrows what "Show All" fits to, which is worth saying in
     * the editor because it does not look like a bound.</p>
     */
    public static String defaultExtentQuery() {
        return "from param('EventStore')\n"
               + "eval allRows = 1\n"
               + "group by allRows\n"
               + "select allRows, min(EffectiveTime), max(EffectiveTime)";
    }

    /**
     * Builds the StroomQL extraction expression for a single path.
     *
     * <p>For JSON, wraps in {@code jq(Value, ...)}. Keys containing
     * characters that are not valid unquoted jq identifiers (e.g.
     * hyphens) are quoted.</p>
     *
     * <p>For XML, wraps in {@code xpath(Value, ...)} using the path
     * directly (which is expected to be a valid XPath from the root
     * of the Value XML).</p>
     *
     * @param path   the schema path (e.g. {@code ".type"} for JSON,
     *               {@code "/entry/type"} for XML)
     * @param format the value serialisation format
     * @return the StroomQL expression (e.g. {@code jq(Value, ".type")})
     */
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

    /**
     * Derives the column alias for a schema path.
     *
     * <p>This is the <strong>bare</strong> name, not quoted. It is used for two things that
     * must agree: {@link #buildFactsQuery} emits it after {@code AS} (quoting it there),
     * and the results are matched back to their roles by comparing it against the column
     * names the server reports — which are the unescaped form. Quoting here would break
     * that comparison.</p>
     *
     * <p><strong>The mapping is injective:</strong> two different paths always give two
     * different aliases. That matters more than a tidy name, because the consumer looks a
     * column up <em>by</em> alias, so two paths sharing one alias do not merely produce an
     * odd heading — they make two roles resolve to the same column, and the map silently
     * draws with the wrong data. Two previous behaviours broke injectivity:</p>
     *
     * <ul>
     *   <li>JSON replaced hyphens with underscores, so {@code .a-b} and {@code .a_b} both
     *       became {@code a_b}. The key is now used verbatim; a heading reads
     *       {@code tm-world-to-map} rather than {@code tm_world_to_map}, which is also
     *       closer to what the field is actually called.</li>
     *   <li>XML took only the last segment, so {@code /entry/type} and
     *       {@code /entry/meta/type} both became {@code type}. The path is now used with
     *       only its leading {@code /} removed.</li>
     * </ul>
     *
     * <p>The XML change costs some brevity in the results table — a heading reads
     * {@code entry/type} rather than {@code type}. A friendlier scheme would have to
     * disambiguate duplicates across the whole schema, and the obvious way to do that
     * (suffixing {@code _2}) makes the alias depend on schema <em>order</em>, so
     * reordering rows in the Settings grid would silently reassign columns. A stable,
     * order-independent alias is worth a longer heading.</p>
     *
     * <p>Special characters need no escaping here: whatever the path contains, the alias is
     * quoted where it is emitted.</p>
     *
     * @param path   the schema path
     * @param format the value serialisation format
     * @return the bare column alias; quote it before putting it in query text
     */
    public static String buildColumnAlias(final String path,
                                          final ValueFormat format) {
        return switch (format) {
            case JSON -> ValuePathAccessor.toKey(path);
            case XML -> path.startsWith("/")
                    ? path.substring(1)
                    : path;
        };
    }

    /**
     * Returns {@code true} if the key contains characters that require
     * quoting in a jq expression (anything other than alphanumerics
     * and underscores).
     */
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
