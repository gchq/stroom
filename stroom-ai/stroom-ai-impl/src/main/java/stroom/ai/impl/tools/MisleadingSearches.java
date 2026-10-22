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
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Searches through Stroom whose answer would mislead (ported from the Stroom MCP server's `_searchable`, with
/// `_cidr`): on Elasticsearch, Stroom (7.13) finds nothing for STARTS_WITH and CONTAINS, nor for a wildcard on an ip
/// field, and matches every document for IS_NULL and IS_NOT_NULL, without an error; on Lucene it finds nothing for
/// STARTS_WITH and ENDS_WITH, for CONTAINS on a keyword field, nor for a range on a text field; IN takes values
/// separated by commas. A field no search finds (not indexed) is refused too.
///
/// search_events refuses such a search of an index, and run_stroomql such a StroomQL query, each saying what to
/// use instead.
public final class MisleadingSearches {

    /// An Elasticsearch index's backend.
    public static final String ELASTICSEARCH = "elasticsearch";
    /// A Lucene index's backend.
    public static final String LUCENE = "lucene";
    /// What a refusal starts with.
    public static final String REFUSAL = "These searches would give a misleading answer: ";

    /// The range conditions.
    static final Set<Condition> RANGES = EnumSet.of(Condition.BETWEEN, Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO, Condition.LESS_THAN, Condition.LESS_THAN_OR_EQUAL_TO);
    private static final Set<String> IP_TYPES = Set.of("ip", "ipv4_address");

    private MisleadingSearches() {
        // Static only.
    }

    /// The refusal for searches whose answer would mislead, if any would.
    ///
    /// @param backend    [#ELASTICSEARCH] or [#LUCENE].
    /// @param searches   The searches.
    /// @param ipFields   The index's ip fields (Elasticsearch).
    /// @param exact      Exact-match checks, each a field and a value.
    /// @param luceneText The Lucene index's text fields and their analyzers (e.g. KEYWORD).
    /// @param unindexed  Fields no search finds, with why.
    /// @return The refusal, saying what to use instead, or empty if every search can be trusted.
    public static Optional<String> refusal(final String backend,
                                           final List<Search> searches,
                                           final Set<String> ipFields,
                                           final List<Exact> exact,
                                           final Map<String, String> luceneText,
                                           final Map<String, String> unindexed) {
        final List<String> problems = problems(backend, searches, ipFields, exact, luceneText, unindexed);
        return problems.isEmpty()
                ? Optional.empty()
                : Optional.of(REFUSAL + String.join("; ", problems));
    }

    /// What is wrong with each search whose answer would mislead.
    ///
    /// @param backend    [#ELASTICSEARCH] or [#LUCENE].
    /// @param searches   The searches.
    /// @param ipFields   The index's ip fields (Elasticsearch).
    /// @param exact      Exact-match checks, each a field and a value.
    /// @param luceneText The Lucene index's text fields and their analyzers (e.g. KEYWORD).
    /// @param unindexed  Fields no search finds, with why.
    /// @return A problem per search that would mislead, saying what to use instead.
    public static List<String> problems(final String backend,
                                        final List<Search> searches,
                                        final Set<String> ipFields,
                                        final List<Exact> exact,
                                        final Map<String, String> luceneText,
                                        final Map<String, String> unindexed) {
        final boolean elastic = ELASTICSEARCH.equals(backend);
        final boolean lucene = LUCENE.equals(backend);
        final Set<String> ips = NullSafe.set(ipFields);
        final List<String> problems = new ArrayList<>();
        NullSafe.map(unindexed).forEach((name, why) -> problems.add(name + ": " + why + ", so no search through "
                                                                    + "Stroom finds it (it is kept, and a dashboard "
                                                                    + "column still shows it): leave it out of the "
                                                                    + "checks"));
        for (final Exact check : NullSafe.list(exact)) {
            if (elastic && ips.contains(check.field()) && NullSafe.string(check.value()).contains("*")) {
                problems.add(check.field() + " = '" + check.value() + "': " + check.field() + " is an ip field, "
                             + "where a wildcard finds nothing; use the CIDR range '" + cidr(check.value()) + "'");
            }
        }
        for (final Search search : NullSafe.list(searches)) {
            problems.addAll(problems(elastic, lucene, search, ips, NullSafe.map(luceneText)));
        }
        return problems;
    }

    // What is wrong with one search, if anything.
    private static List<String> problems(final boolean elastic,
                                         final boolean lucene,
                                         final Search search,
                                         final Set<String> ipFields,
                                         final Map<String, String> luceneText) {
        final String field = search.field();
        final Condition condition = search.condition();
        final String value = NullSafe.string(search.value());
        final String bare = stripWildcards(value);
        final List<String> problems = new ArrayList<>();
        if (elastic && ipFields.contains(field) && condition == Condition.EQUALS && value.contains("*")) {
            // Seen in VS Code: IpAddress EQUALS 192.0.2.* found 0 of the 10 documents it should have.
            problems.add(field + " EQUALS '" + value + "': " + field + " is an ip field, where a wildcard finds "
                         + "nothing; use EQUALS '" + cidr(value) + "' (a CIDR range)");
        }
        if (condition == Condition.IN && !value.contains(",") && value.strip().contains(" ")) {
            problems.add(field + " IN '" + value + "': separate the values with commas (one value holding a space "
                         + "is EQUALS '" + value + "')");
        } else if (lucene && condition == Condition.IN && anyHoldsASpace(value)) {
            // Seen (eval case 15): IN 'Main entrance,Server room' found 0 where EQUALS 'Server room' found 2.
            problems.add(field + " IN '" + value + "': Stroom finds nothing with IN on a Lucene index when a value "
                         + "holds a space; search each such value with EQUALS");
        }
        if (elastic && (condition == Condition.STARTS_WITH || condition == Condition.CONTAINS)) {
            final String pattern = condition == Condition.STARTS_WITH
                    ? bare + "*"
                    : "*" + bare + "*";
            problems.add(field + " " + condition.name() + " '" + value + "': Stroom finds nothing with "
                         + condition.name() + " on Elasticsearch; use EQUALS '" + pattern + "' (a wildcard) or "
                         + "MATCHES_REGEX");
        }
        // Seen on the local Stroom (eval case 22, Haiku): each of these found 0 where EQUALS with a wildcard found
        // them all, and the agent spent twenty turns on checks that could never pass.
        if (lucene && (condition == Condition.STARTS_WITH || condition == Condition.ENDS_WITH)) {
            // Seen: STARTS_WITH 'A*', given back as EQUALS 'A**'.
            final String pattern = condition == Condition.STARTS_WITH
                    ? bare + "*"
                    : "*" + bare;
            problems.add(field + " " + condition.name() + " '" + value + "': Stroom finds nothing with "
                         + condition.name() + " on a Lucene index; use EQUALS '" + pattern + "' (a wildcard)");
        }
        final String analyzer = luceneText.get(field);
        if (lucene && condition == Condition.CONTAINS && "KEYWORD".equals(analyzer)) {
            problems.add(field + " CONTAINS '" + value + "': " + field + " is a keyword field, where Stroom finds "
                         + "nothing with CONTAINS; use EQUALS '*" + bare + "*' (a wildcard)");
        }
        if (lucene && RANGES.contains(condition) && NullSafe.isNonEmptyString(analyzer)) {
            problems.add(field + " " + condition.name() + " '" + value + "': " + field
                         + " is a text field in Lucene, where Stroom finds nothing with a range; use EQUALS with a "
                         + "wildcard (an address range as '10.1.*') or IN with the values");
        }
        if (elastic && condition == Condition.IS_NOT_NULL) {
            problems.add(field + " IS_NOT_NULL: Stroom matches every document with IS_NOT_NULL on Elasticsearch; "
                         + "use EQUALS '*' (any value) instead");
        }
        if (elastic && condition == Condition.IS_NULL) {
            problems.add(field + " IS_NULL: Stroom matches every document with IS_NULL on Elasticsearch; check "
                         + "EQUALS '*' (any value) with the expected count instead");
        }
        return problems;
    }

    /// The CIDR range a wildcard address means: 192.0.2.* is 192.0.2.0/24.
    ///
    /// @param value An address with a wildcard, e.g. `10.0.*`.
    /// @return Its range, e.g. `10.0.0.0/16`; `10.0.0.0/8` if it starts with no number.
    public static String cidr(final String value) {
        final String text = NullSafe.string(value);
        final int star = text.indexOf('*');
        final String before = star < 0
                ? text
                : text.substring(0, star);
        final List<String> known = new ArrayList<>();
        for (final String part : before.split("\\.")) {
            if (known.size() < 3 && !part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
                known.add(part);
            }
        }
        if (known.isEmpty()) {
            return "10.0.0.0/8";
        }
        final List<String> octets = new ArrayList<>(known);
        while (octets.size() < 4) {
            octets.add("0");
        }
        return String.join(".", octets) + "/" + (8 * known.size());
    }

    /// What IS_NOT_NULL and IS_NULL mean on Elasticsearch, where Stroom matches every document with either: any
    /// value (EQUALS '*') and no value (NOT_EQUALS '*').
    ///
    /// @param condition A condition.
    /// @return The condition it is searched as on Elasticsearch, or empty if it is searched as it is.
    public static Optional<Condition> elasticsearchNullAs(final Condition condition) {
        if (condition == Condition.IS_NOT_NULL) {
            return Optional.of(Condition.EQUALS);
        } else if (condition == Condition.IS_NULL) {
            return Optional.of(Condition.NOT_EQUALS);
        }
        return Optional.empty();
    }

    /// The backend of an index doc.
    ///
    /// @param dataSource A data source.
    /// @return [#ELASTICSEARCH] for an Elastic Index, [#LUCENE] for a Lucene Index, or empty for any other.
    public static Optional<String> backendOf(final DocRef dataSource) {
        final String type = NullSafe.get(dataSource, DocRef::getType);
        if (ElasticIndexDoc.TYPE.equals(type)) {
            return Optional.of(ELASTICSEARCH);
        } else if (LuceneIndexDoc.TYPE.equals(type)) {
            return Optional.of(LUCENE);
        }
        return Optional.empty();
    }

    /// @param fields An index's fields, as Stroom lists them.
    /// @return Those that hold addresses (ip, ipv4_address), where a wildcard finds nothing on Elasticsearch.
    public static Set<String> ipFields(final List<QueryField> fields) {
        final Set<String> ipFields = new LinkedHashSet<>();
        for (final QueryField field : NullSafe.list(fields)) {
            final String type = NullSafe.get(field.getFldType(), fieldType -> fieldType.name()
                    .toLowerCase(Locale.ROOT));
            if (field.getFldName() != null && IP_TYPES.contains(type)) {
                ipFields.add(field.getFldName());
            }
        }
        return ipFields;
    }

    /// Of the fields searched, those no search finds, with why: a Lucene field not indexed, or one Stroom lists
    /// as not queryable.
    ///
    /// @param dataSource The index.
    /// @param fields     Its fields, as Stroom lists them.
    /// @param searched   The fields searched.
    /// @return Each such field, with why.
    public static Map<String, String> unindexed(final DocRef dataSource,
                                                final List<QueryField> fields,
                                                final Collection<String> searched) {
        final Map<String, String> unindexed = new LinkedHashMap<>();
        for (final QueryField field : NullSafe.list(fields)) {
            if (!field.queryable() && searched.contains(field.getFldName())) {
                unindexed.put(field.getFldName(), LuceneIndexDoc.TYPE.equals(dataSource.getType())
                        ? "the index doc's field isn't indexed"
                        : "Stroom lists it as not searchable");
            }
        }
        return unindexed;
    }

    /// @param expression A query's expression.
    /// @return Its enabled terms, at any depth, as searches.
    public static List<Search> searches(final ExpressionOperator expression) {
        final List<Search> searches = new ArrayList<>();
        addSearches(expression, searches);
        return searches;
    }

    private static void addSearches(final ExpressionItem item, final List<Search> searches) {
        if (item == null || !item.enabled()) {
            return;
        }
        if (item instanceof final ExpressionOperator operator) {
            NullSafe.list(operator.getChildren()).forEach(child -> addSearches(child, searches));
        } else if (item instanceof final ExpressionTerm term && term.getField() != null
                   && term.getCondition() != null) {
            searches.add(new Search(term.getField(), term.getCondition(), NullSafe.string(term.getValue())));
        }
    }

    private static boolean anyHoldsASpace(final String value) {
        for (final String part : value.split(",", -1)) {
            if (part.strip().contains(" ")) {
                return true;
            }
        }
        return false;
    }

    // The value without the wildcards at either end, as Python's str.strip('*').
    private static String stripWildcards(final String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '*') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '*') {
            end--;
        }
        return value.substring(start, end);
    }


    // --------------------------------------------------------------------------------


    /// One search, as a person would make it on a dashboard.
    ///
    /// @param field     The index's field.
    /// @param condition The condition.
    /// @param value     The value: BETWEEN 'from,to'; IN values separated by commas; EQUALS takes * wildcards.
    public record Search(String field, Condition condition, String value) {

    }

    /// An exact-match check.
    ///
    /// @param field The index's field.
    /// @param value The value it must have.
    public record Exact(String field, String value) {

    }
}
