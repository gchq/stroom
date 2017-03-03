/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.Version;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.index.server.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFieldType;
import stroom.query.DateExpressionParser;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Convert our query objects to a LUCENE query.
 */
public class SearchExpressionQueryBuilder {
    private static final String DELIMITER = ",";
    private static final Pattern NON_WORD_OR_WILDCARD = Pattern.compile("[^a-zA-Z0-9+*?]");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern MULTIPLE_WILDCARD = Pattern.compile("[+]+");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile("[ ]+");
    private final IndexFieldsMap indexFieldsMap;
    private final DictionaryService dictionaryService;
    private final int maxBooleanClauseCount;
    private final String timeZoneId;
    private final long nowEpochMilli;

    public SearchExpressionQueryBuilder(final DictionaryService dictionaryService, final IndexFieldsMap indexFieldsMap,
                                        final int maxBooleanClauseCount, final String timeZoneId, final long nowEpochMilli) {
        this.dictionaryService = dictionaryService;
        this.indexFieldsMap = indexFieldsMap;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
        this.timeZoneId = timeZoneId;
        this.nowEpochMilli = nowEpochMilli;
    }

    public SearchExpressionQuery buildQuery(final Version matchVersion, final ExpressionOperator expression) {
        if (expression == null) {
            throw new SearchException("No search expression has been provided!");
        }
        if (!hasChildren(expression)) {
            throw new SearchException("No search terms have been specified!");
        }

        BooleanQuery.setMaxClauseCount(maxBooleanClauseCount);

        // Build a query.
        final Set<String> terms = new HashSet<>();
        final Query query = getQuery(matchVersion, expression, terms);
        return new SearchExpressionQuery(query, terms);
    }

    private Query getQuery(final Version matchVersion, final ExpressionItem item, final Set<String> terms) {
        if (item.enabled()) {
            if (item instanceof ExpressionTerm) {
                // Create queries for single terms.
                final ExpressionTerm term = (ExpressionTerm) item;
                return getTermQuery(term, matchVersion, terms);

            } else if (item instanceof ExpressionOperator) {
                // Create queries for expression tree nodes.
                final ExpressionOperator operator = (ExpressionOperator) item;
                if (hasChildren(operator)) {
                    final List<Query> innerChildQueries = new ArrayList<>();
                    for (final ExpressionItem childItem : operator.getChildren()) {
                        final Query childQuery = getQuery(matchVersion, childItem, terms);
                        if (childQuery != null) {
                            innerChildQueries.add(childQuery);
                        }
                    }

                    if (innerChildQueries.size() > 0) {
                        final Occur occur = getOccur(operator);

                        if (innerChildQueries.size() == 1) {
                            final Query child = innerChildQueries.get(0);

                            // Add negation to single items if required.
                            if (Occur.MUST_NOT.equals(occur)) {
                                final Builder builder = new Builder();
                                builder.add(child, occur);
                                return builder.build();
                            }

                            return child;

                        } else {
                            final Builder builder = new Builder();
                            for (final Query child : innerChildQueries) {
                                if (Occur.MUST.equals(occur)) {
                                    // If this is an AND then we can collapse
                                    // down non OR child queries.
                                    if (child instanceof BooleanQuery) {
                                        final BooleanQuery innerBoolean = (BooleanQuery) child;
                                        final Builder orTermsBuilder = new Builder();
                                        for (final BooleanClause clause : innerBoolean.clauses()) {
                                            if (Occur.MUST_NOT.equals(clause.getOccur())) {
                                                builder.add(clause.getQuery(), Occur.MUST_NOT);
                                            } else if (Occur.MUST.equals(clause.getOccur())) {
                                                builder.add(clause.getQuery(), Occur.MUST);
                                            } else {
                                                orTermsBuilder.add(clause);
                                            }
                                        }

                                        final BooleanQuery orTerms = orTermsBuilder.build();
                                        if (orTerms.clauses().size() > 0) {
                                            if (orTerms.clauses().size() == 1) {
                                                // Collapse single term.
                                                builder.add(orTerms.clauses().get(0).getQuery(), occur);
                                            } else {
                                                builder.add(orTerms, occur);
                                            }
                                        }

                                    } else {
                                        builder.add(child, occur);
                                    }
                                } else if (Occur.MUST_NOT.equals(occur)) {
                                    // Remove double negation.
                                    if (child instanceof BooleanQuery) {
                                        final BooleanQuery innerBoolean = (BooleanQuery) child;
                                        final Builder orTermsBuilder = new Builder();
                                        for (final BooleanClause clause : innerBoolean.clauses()) {
                                            if (Occur.MUST_NOT.equals(clause.getOccur())) {
                                                builder.add(clause.getQuery(), Occur.MUST);
                                            } else if (Occur.MUST.equals(clause.getOccur())) {
                                                builder.add(clause.getQuery(), Occur.MUST_NOT);
                                            } else {
                                                orTermsBuilder.add(clause);
                                            }
                                        }

                                        final BooleanQuery orTerms = orTermsBuilder.build();
                                        if (orTerms.clauses().size() > 0) {
                                            if (orTerms.clauses().size() == 1) {
                                                // Collapse single term.
                                                builder.add(orTerms.clauses().get(0).getQuery(), occur);
                                            } else {
                                                builder.add(orTerms, occur);
                                            }
                                        }

                                    } else {
                                        builder.add(child, occur);
                                    }
                                } else {
                                    builder.add(child, occur);
                                }
                            }
                            return builder.build();
                        }
                    }
                }
            }
        }

        return null;
    }

    private Query getTermQuery(final ExpressionTerm term, final Version matchVersion, final Set<String> terms) {
        String field = term.getField();
        final Condition condition = term.getCondition();
        String value = term.getValue();
        final DocRef dictionary = term.getDictionary();

        // Clean strings to remove unwanted whitespace that the user may have
        // added accidentally.
        if (field != null) {
            field = field.trim();
        }
        if (value != null) {
            value = value.trim();
        }

        // Try and find the referenced field.
        if (field == null || field.length() == 0) {
            throw new SearchException("Field not set");
        }
        final IndexField indexField = indexFieldsMap.get(field);
        if (indexField == null) {
            throw new SearchException("Field not found in index: " + field);
        }
        final String fieldName = indexField.getFieldName();

        // Ensure an appropriate value has been provided for the condition type.
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (dictionary == null || dictionary.getUuid() == null) {
                throw new SearchException("Dictionary not set for field: " + field);
            }
        } else {
            if (value == null || value.length() == 0) {
                return null;
            }
        }

        // Create a query based on the field type and condition.
        if (indexField.getFieldType().isNumeric()) {
            switch (condition) {
                case EQUALS:
                    final Long num1 = getNumber(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, num1, num1, true, true);
                case CONTAINS:
                    final Long num2 = getNumber(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, num2, num2, true, true);
                case GREATER_THAN:
                    return NumericRangeQuery.newLongRange(fieldName, getNumber(fieldName, value), Long.MAX_VALUE, false,
                            true);
                case GREATER_THAN_OR_EQUAL_TO:
                    return NumericRangeQuery.newLongRange(fieldName, getNumber(fieldName, value), Long.MAX_VALUE, true,
                            true);
                case LESS_THAN:
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getNumber(fieldName, value), true,
                            false);
                case LESS_THAN_OR_EQUAL_TO:
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getNumber(fieldName, value), true,
                            true);
                case BETWEEN:
                    final long[] between = getNumbers(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newLongRange(fieldName, between[0], between[1], true, true);
                case IN:
                    return getNumericIn(fieldName, value);
                case IN_DICTIONARY:
                    return getDictionary(fieldName, dictionary, indexField, matchVersion, terms);
                default:
                    throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
            switch (condition) {
                case EQUALS:
                    final Long date1 = getDate(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, date1, date1, true, true);
                case CONTAINS:
                    final Long date2 = getDate(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, date2, date2, true, true);
                case GREATER_THAN:
                    return NumericRangeQuery.newLongRange(fieldName, 8, getDate(fieldName, value), Long.MAX_VALUE, false,
                            true);
                case GREATER_THAN_OR_EQUAL_TO:
                    return NumericRangeQuery.newLongRange(fieldName, 8, getDate(fieldName, value), Long.MAX_VALUE, true,
                            true);
                case LESS_THAN:
                    return NumericRangeQuery.newLongRange(fieldName, 8, Long.MIN_VALUE, getDate(fieldName, value), true,
                            false);
                case LESS_THAN_OR_EQUAL_TO:
                    return NumericRangeQuery.newLongRange(fieldName, 8, Long.MIN_VALUE, getDate(fieldName, value), true,
                            true);
                case BETWEEN:
                    final long[] between = getDates(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 dates needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From date must occur before to date");
                    }
                    return NumericRangeQuery.newLongRange(fieldName, 8, between[0], between[1], true, true);
                case IN:
                    return getDateIn(fieldName, value);
                case IN_DICTIONARY:
                    return getDictionary(fieldName, dictionary, indexField, matchVersion, terms);
                default:
                    throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        } else {
            switch (condition) {
                case EQUALS:
                    return getSubQuery(matchVersion, indexField, value, terms, false);
                case CONTAINS:
                    return getContains(fieldName, value, indexField, matchVersion, terms);
                case IN:
                    return getIn(fieldName, value, indexField, matchVersion, terms);
                case IN_DICTIONARY:
                    return getDictionary(fieldName, dictionary, indexField, matchVersion, terms);
                default:
                    throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldType().getDisplayValue() + " field type");
            }
        }
    }

    private Query getNumericIn(final String fieldName, final String value) {
        final long[] in = getNumbers(fieldName, value);
        if (in != null && in.length > 0) {
            if (in.length == 1) {
                final long num = in[0];
                return NumericRangeQuery.newLongRange(fieldName, num, num, true, true);
            } else {
                final Builder builder = new Builder();
                for (final long num : in) {
                    final Query q = NumericRangeQuery.newLongRange(fieldName, num, num, true, true);
                    builder.add(q, Occur.SHOULD);
                }
                return builder.build();
            }
        }

        return null;
    }

    private Query getDateIn(final String fieldName, final String value) {
        final long[] in = getDates(fieldName, value);
        if (in != null && in.length > 0) {
            if (in.length == 1) {
                final long date = in[0];
                return NumericRangeQuery.newLongRange(fieldName, date, date, true, true);
            } else {
                final Builder builder = new Builder();
                for (final long date : in) {
                    final Query q = NumericRangeQuery.newLongRange(fieldName, date, date, true, true);
                    builder.add(q, Occur.SHOULD);
                }
                return builder.build();
            }
        }

        return null;
    }

    private Query getContains(final String fieldName, final String value, final IndexField indexField,
                              final Version matchVersion, final Set<String> terms) {
        final Query query = getSubQuery(matchVersion, indexField, value, terms, false);
        return modifyOccurrence(query, Occur.MUST);
    }

    private Query getIn(final String fieldName, final String value, final IndexField indexField,
                        final Version matchVersion, final Set<String> terms) {
        final Query query = getSubQuery(matchVersion, indexField, value, terms, true);
        return modifyOccurrence(query, Occur.SHOULD);
    }

    private Query modifyOccurrence(final Query query, final Occur occur) {
        // Change all occurs to must as we want to insist that all terms exist
        // in the matched documents.
        if (query instanceof BooleanQuery) {
            final BooleanQuery bq = (BooleanQuery) query;
            final Builder builder = new Builder();
            for (final BooleanClause bc : bq.clauses()) {
                builder.add(bc.getQuery(), occur);
            }
            return builder.build();
        }
        return query;
    }

    private Query getDictionary(final String fieldName, final DocRef docRef,
                                final IndexField indexField, final Version matchVersion, final Set<String> terms) {
        final String[] wordArr = loadWords(docRef);
        if (wordArr != null) {
            final Builder builder = new Builder();
            for (final String val : wordArr) {
                Query query;

                if (indexField.getFieldType().isNumeric()) {
                    query = getNumericIn(fieldName, val);
                } else if (IndexFieldType.DATE_FIELD.equals(indexField.getFieldType())) {
                    query = getDateIn(fieldName, val);
                } else {
                    query = getSubQuery(matchVersion, indexField, val, terms, false);
                }

                if (query != null) {
                    // Dictionary terms on one line must all exist in the
                    // matching documents so change to must.
                    query = modifyOccurrence(query, Occur.MUST);
                    builder.add(query, Occur.SHOULD);
                }
            }
            return builder.build();
        }

        return null;
    }

    private String[] loadWords(final DocRef docRef) {
        final Dictionary dictionary = dictionaryService.loadByUuid(docRef.getUuid());
        if (dictionary == null) {
            throw new SearchException("Dictionary \"" + docRef + "\" not found");
        }

        final String words = dictionary.getData();
        if (words != null) {
            return words.trim().split("\n");
        }

        return null;
    }

    private Occur getOccur(final ExpressionOperator operator) {
        if (operator.getOp() != null) {
            switch (operator.getOp()) {
                case AND:
                    return Occur.MUST;
                case OR:
                    return Occur.SHOULD;
                case NOT:
                    return Occur.MUST_NOT;
            }
        }

        return Occur.MUST;
    }

    private Query getSubQuery(final Version matchVersion, final IndexField field, final String value,
                              final Set<String> terms, final boolean in) {
        Query query = null;

        // Store terms for hit highlighting.
        String highlight = value;
        highlight = NON_WORD.matcher(highlight).replaceAll(" ");
        highlight = highlight.trim();
        highlight = MULTIPLE_SPACE.matcher(highlight).replaceAll(" ");
        final String[] highlights = highlight.split(" ");
        Collections.addAll(terms, highlights);

        // If we have omitted term frequencies and positions for this field then
        // we can't expect to do a sentence match. In this case we need to
        // modify the query so that each word becomes a new term in a boolean
        // query.
        String val = value.trim();
        if (in || !AnalyzerType.KEYWORD.equals(field.getAnalyzerType())) {
            // If the field has been analysed then we need to analyse the search
            // query to create matching terms.
            final Analyzer analyzer = AnalyzerFactory.create(field.getAnalyzerType(),
                    field.isCaseSensitive());

            if (!field.isTermPositions()) {
                val = NON_WORD_OR_WILDCARD.matcher(val).replaceAll(" ");
                val = val.trim();
                val = MULTIPLE_SPACE.matcher(val).replaceAll(" +");
                val = MULTIPLE_WILDCARD.matcher(val).replaceAll("+");
            }

            if (val.length() > 0) {
                final StandardQueryParser queryParser = new StandardQueryParser(analyzer);
                queryParser.setAllowLeadingWildcard(true);
                queryParser.setLowercaseExpandedTerms(!field.isCaseSensitive());

                try {
                    query = queryParser.parse(val, field.getFieldName());
                } catch (final QueryNodeException e) {
                    throw new SearchException("Unable to parse query term '" + val + "'", e);
                }
            }

        } else {
            if (val.length() > 0) {
                // As this is just indexed as a keyword we only want to search
                // for the term.
                if (!field.isCaseSensitive()) {
                    val = value.toLowerCase();
                }

                final Term term = new Term(field.getFieldName(), val);
                final boolean termContainsWildcard = (val.indexOf('*') != -1) || (val.indexOf('?') != -1);
                if (termContainsWildcard) {
                    query = new WildcardQuery(new Term(field.getFieldName(), val));
                } else {
                    query = new TermQuery(term);
                }
            }
        }

        return query;
    }

    private boolean hasChildren(final ExpressionOperator operator) {
        if (operator != null && operator.enabled() && operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    if (child instanceof ExpressionOperator) {
                        final ExpressionOperator childOperator = (ExpressionOperator) child;
                        if (hasChildren(childOperator)) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private long getDate(final String fieldName, final String value) {
        try {
            return new DateExpressionParser().parse(value, timeZoneId, nowEpochMilli).toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new SearchException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    private long[] getDates(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = getDate(fieldName, values[i].trim());
        }

        return dates;
    }

    private long getNumber(final String fieldName, final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new SearchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private long[] getNumbers(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final long[] numbers = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getNumber(fieldName, values[i].trim());
        }

        return numbers;
    }

    static class SearchExpressionQuery {
        private final Query query;
        private final Set<String> terms;

        SearchExpressionQuery(final Query query, final Set<String> terms) {
            this.query = query;
            this.terms = terms;
        }

        Query getQuery() {
            return query;
        }

        Set<String> getTerms() {
            return terms;
        }
    }
}
