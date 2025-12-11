/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.lucene553;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.lucene553.analyser.AnalyzerFactory;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.language.token.InTermsUtil;
import stroom.search.impl.SearchException;

import org.apache.lucene553.analysis.Analyzer;
import org.apache.lucene553.index.Term;
import org.apache.lucene553.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene553.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene553.search.BooleanClause;
import org.apache.lucene553.search.BooleanClause.Occur;
import org.apache.lucene553.search.BooleanQuery;
import org.apache.lucene553.search.BooleanQuery.Builder;
import org.apache.lucene553.search.MatchAllDocsQuery;
import org.apache.lucene553.search.MatchNoDocsQuery;
import org.apache.lucene553.search.NumericRangeQuery;
import org.apache.lucene553.search.Query;
import org.apache.lucene553.search.TermQuery;
import org.apache.lucene553.search.WildcardQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Convert our query objects to a LUCENE query.
 */
class SearchExpressionQueryBuilder {

    private static final String DELIMITER = ",";
    private static final Pattern NON_WORD_OR_WILDCARD = Pattern.compile("[^a-zA-Z0-9+*?]");
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern MULTIPLE_WILDCARD = Pattern.compile("[+]+");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile("[ ]+");
    private final DocRef indexDocRef;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;
    private final DateTimeSettings dateTimeSettings;

    SearchExpressionQueryBuilder(final DocRef indexDocRef,
                                 final IndexFieldCache indexFieldCache,
                                 final WordListProvider wordListProvider,
                                 final int maxBooleanClauseCount,
                                 final DateTimeSettings dateTimeSettings) {
        this.indexDocRef = indexDocRef;
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
        this.dateTimeSettings = dateTimeSettings;
    }

    public SearchExpressionQuery buildQuery(final ExpressionOperator expression) {
        Set<String> terms = Collections.emptySet();
        Query query = null;
        if (expression != null) {
            BooleanQuery.setMaxClauseCount(maxBooleanClauseCount);

            // Build a query.
            terms = new HashSet<>();
            query = getQuery(expression, terms);
        }

        if (query == null) {
            query = new MatchAllDocsQuery();
        } else if (query instanceof final BooleanQuery booleanQuery) {
            final boolean hasInclusiveClauses = hasInclusiveClauses(query);
            if (!hasInclusiveClauses) {
                final Builder builder = new Builder();
                builder.add(new MatchAllDocsQuery(), Occur.MUST);
                for (final BooleanClause booleanClause : booleanQuery.clauses()) {
                    builder.add(booleanClause);
                }
                query = builder.build();
            }
        }
        return new SearchExpressionQuery(query, terms);
    }

    private boolean hasInclusiveClauses(final Query query) {
        if (query instanceof final BooleanQuery booleanQuery) {
            for (final BooleanClause booleanClause : booleanQuery.clauses()) {
                if (!Occur.MUST_NOT.equals(booleanClause.getOccur())) {
                    return true;
                }
                final Query subQuery = booleanClause.getQuery();
                if (hasInclusiveClauses(subQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Query getQuery(final ExpressionItem item,
                           final Set<String> terms) {
        if (item.enabled()) {
            if (item instanceof final ExpressionTerm term) {
                // Create queries for single terms.
                return getTermQuery(term, terms);

            } else if (item instanceof final ExpressionOperator operator) {
                // Create queries for expression tree nodes.
                if (hasChildren(operator)) {
                    final List<Query> innerChildQueries = new ArrayList<>();
                    for (final ExpressionItem childItem : operator.getChildren()) {
                        final Query childQuery = getQuery(childItem, terms);
                        if (childQuery != null) {
                            innerChildQueries.add(childQuery);
                        }
                    }

                    if (!innerChildQueries.isEmpty()) {
                        final Occur occur = getOccur(operator);

                        if (innerChildQueries.size() == 1) {
                            final Query child = innerChildQueries.getFirst();

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
                                    if (child instanceof final BooleanQuery innerBoolean) {
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
                                        if (!orTerms.clauses().isEmpty()) {
                                            if (orTerms.clauses().size() == 1) {
                                                // Collapse single term.
                                                builder.add(orTerms.clauses().getFirst().getQuery(), occur);
                                            } else {
                                                builder.add(orTerms, occur);
                                            }
                                        }

                                    } else {
                                        builder.add(child, occur);
                                    }
                                } else if (Occur.MUST_NOT.equals(occur)) {
                                    // Remove double negation.
                                    if (child instanceof final BooleanQuery innerBoolean) {
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
                                        if (!orTerms.clauses().isEmpty()) {
                                            if (orTerms.clauses().size() == 1) {
                                                // Collapse single term.
                                                builder.add(orTerms.clauses().getFirst().getQuery(), occur);
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

    private Query getTermQuery(final ExpressionTerm term,
                               final Set<String> terms) {
        String field = term.getField();
        final Condition condition = term.getCondition();
        String value = term.getValue();
        final DocRef docRef = term.getDocRef();

        // Clean strings to remove unwanted whitespace that the user may have
        // added accidentally.
        if (field != null) {
            field = field.trim();
        }
        if (value != null) {
            value = value.trim();
        }

        // Try and find the referenced field.
        if (field == null || field.isEmpty()) {
            throw new SearchException("Field not set");
        }
        final IndexField indexField = indexFieldCache.get(indexDocRef, field);
        if (indexField == null) {
            return new MatchNoDocsQuery();
        }
        final String fieldName = indexField.getFldName();

        // Ensure an appropriate value has been provided for the condition type.
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new SearchException("Dictionary not set for field: " + field);
            }
        } else if (Condition.IS_DOC_REF.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new SearchException("Doc Ref not set for field: " + field);
            }
        } else {
            if (value == null || value.isEmpty()) {
                return null;
            }
        }


        // Create a query based on the field type and condition.
        if (FieldType.INTEGER.equals(indexField.getFldType())) {
            switch (condition) {
                case EQUALS -> {
                    final int num1 = getInt(fieldName, value);
                    return NumericRangeQuery.newIntRange(fieldName, num1, num1, true, true);
                }
                case NOT_EQUALS -> {
                    final int num1 = getInt(fieldName, value);
                    return negate(NumericRangeQuery.newIntRange(fieldName, num1, num1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newIntRange(fieldName, getInt(fieldName, value), Integer.MAX_VALUE, false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newIntRange(fieldName, getInt(fieldName, value), Integer.MAX_VALUE, true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newIntRange(fieldName, Integer.MIN_VALUE, getInt(fieldName, value), true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newIntRange(fieldName, Integer.MIN_VALUE, getInt(fieldName, value), true,
                            true);
                }
                case BETWEEN -> {
                    final int[] between = getInts(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newIntRange(fieldName, between[0], between[1], true, true);
                }
                case IN -> {
                    return getIntIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else if (FieldType.LONG.equals(indexField.getFldType())) {
            switch (condition) {
                case EQUALS -> {
                    final Long num1 = getLong(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, num1, num1, true, true);
                }
                case NOT_EQUALS -> {
                    final Long num1 = getLong(fieldName, value);
                    return negate(NumericRangeQuery.newLongRange(fieldName, num1, num1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName, getLong(fieldName, value), Long.MAX_VALUE, false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName, getLong(fieldName, value), Long.MAX_VALUE, true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getLong(fieldName, value), true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getLong(fieldName, value), true,
                            true);
                }
                case BETWEEN -> {
                    final long[] between = getLongs(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newLongRange(fieldName, between[0], between[1], true, true);
                }
                case IN -> {
                    return getLongIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else if (FieldType.FLOAT.equals(indexField.getFldType())) {
            switch (condition) {
                case EQUALS -> {
                    final Float num1 = getFloat(fieldName, value);
                    return NumericRangeQuery.newFloatRange(fieldName, num1, num1, true, true);
                }
                case NOT_EQUALS -> {
                    final Float num1 = getFloat(fieldName, value);
                    return negate(NumericRangeQuery.newFloatRange(fieldName, num1, num1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newFloatRange(fieldName,
                            getFloat(fieldName, value),
                            Float.MAX_VALUE,
                            false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newFloatRange(fieldName, getFloat(fieldName, value), Float.MAX_VALUE, true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newFloatRange(fieldName, Float.MIN_VALUE, getFloat(fieldName, value), true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newFloatRange(fieldName, Float.MIN_VALUE, getFloat(fieldName, value), true,
                            true);
                }
                case BETWEEN -> {
                    final float[] between = getFloats(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newFloatRange(fieldName, between[0], between[1], true, true);
                }
                case IN -> {
                    return getFloatIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else if (FieldType.DOUBLE.equals(indexField.getFldType())) {
            switch (condition) {
                case EQUALS -> {
                    final Double num1 = getDouble(fieldName, value);
                    return NumericRangeQuery.newDoubleRange(fieldName, num1, num1, true, true);
                }
                case NOT_EQUALS -> {
                    final Double num1 = getDouble(fieldName, value);
                    return negate(NumericRangeQuery.newDoubleRange(fieldName, num1, num1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newDoubleRange(fieldName,
                            getDouble(fieldName, value),
                            Double.MAX_VALUE,
                            false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newDoubleRange(fieldName,
                            getDouble(fieldName, value),
                            Double.MAX_VALUE,
                            true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newDoubleRange(fieldName,
                            Double.MIN_VALUE,
                            getDouble(fieldName, value),
                            true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newDoubleRange(fieldName,
                            Double.MIN_VALUE,
                            getDouble(fieldName, value),
                            true,
                            true);
                }
                case BETWEEN -> {
                    final double[] between = getDoubles(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newDoubleRange(fieldName, between[0], between[1], true, true);
                }
                case IN -> {
                    return getDoubleIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else if (FieldType.DATE.equals(indexField.getFldType())) {
            switch (condition) {
                case EQUALS -> {
                    final Long date1 = DateExpressionParser.getMs(fieldName, value, dateTimeSettings);
                    return NumericRangeQuery.newLongRange(fieldName, date1, date1, true, true);
                }
                case NOT_EQUALS -> {
                    final Long date1 = DateExpressionParser.getMs(fieldName, value, dateTimeSettings);
                    return negate(NumericRangeQuery.newLongRange(fieldName, date1, date1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName,
                            8,
                            DateExpressionParser.getMs(fieldName, value, dateTimeSettings),
                            Long.MAX_VALUE,
                            false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName,
                            8,
                            DateExpressionParser.getMs(fieldName, value, dateTimeSettings),
                            Long.MAX_VALUE,
                            true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName,
                            8,
                            Long.MIN_VALUE,
                            DateExpressionParser.getMs(fieldName, value, dateTimeSettings),
                            true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName,
                            8,
                            Long.MIN_VALUE,
                            DateExpressionParser.getMs(fieldName, value, dateTimeSettings),
                            true,
                            true);
                }
                case BETWEEN -> {
                    final long[] between = getDates(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 dates needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From date must occur before to date");
                    }
                    return NumericRangeQuery.newLongRange(fieldName, 8, between[0], between[1], true, true);
                }
                case IN -> {
                    return getDateIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else if (indexField.getFldType().isNumeric()) {
            switch (condition) {
                case EQUALS -> {
                    final Long num1 = getLong(fieldName, value);
                    return NumericRangeQuery.newLongRange(fieldName, num1, num1, true, true);
                }
                case NOT_EQUALS -> {
                    final Long num1 = getLong(fieldName, value);
                    return negate(NumericRangeQuery.newLongRange(fieldName, num1, num1, true, true));
                }
                case CONTAINS -> {
                    return getContains(fieldName, value, indexField, terms);
                }
                case GREATER_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName, getLong(fieldName, value), Long.MAX_VALUE, false,
                            true);
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName, getLong(fieldName, value), Long.MAX_VALUE, true,
                            true);
                }
                case LESS_THAN -> {
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getLong(fieldName, value), true,
                            false);
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return NumericRangeQuery.newLongRange(fieldName, Long.MIN_VALUE, getLong(fieldName, value), true,
                            true);
                }
                case BETWEEN -> {
                    final long[] between = getLongs(fieldName, value);
                    if (between.length != 2) {
                        throw new SearchException("2 numbers needed for between query");
                    }
                    if (between[0] >= between[1]) {
                        throw new SearchException("From number must lower than to number");
                    }
                    return NumericRangeQuery.newLongRange(fieldName, between[0], between[1], true, true);
                }
                case IN -> {
                    return getLongIn(fieldName, value);
                }
                case IN_DICTIONARY -> {
                    return getDictionary(fieldName, docRef, indexField, terms);
                }
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            }
        } else {
            return switch (condition) {
                case EQUALS -> getContains(fieldName, value, indexField, terms);
                case NOT_EQUALS -> negate(getContains(fieldName, value, indexField, terms));
//                    return getSubQuery(matchVersion, indexField, value, terms, false);
                case CONTAINS -> getContains(fieldName, value, indexField, terms);
                case IN -> getIn(fieldName, value, indexField, terms);
                case IN_DICTIONARY -> getDictionary(fieldName, docRef, indexField, terms);
                case IS_DOC_REF -> getSubQuery(indexField, docRef.getUuid(), terms);
                default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                                                     + indexField.getFldType().getDisplayValue() + " field type");
            };
        }
    }

    private Query negate(final Query query) {
        // To enable SQL style functionality we have to tell Lucene to match all except the specified term.
        return new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), Occur.SHOULD)
                .add(query, Occur.MUST_NOT)
                .build();
    }

    private Query getIntIn(final String fieldName, final String value) {
        final int[] in = getInts(fieldName, value);
        if (in.length > 0) {
            if (in.length == 1) {
                final int num = in[0];
                return NumericRangeQuery.newIntRange(fieldName, num, num, true, true);
            } else {
                final Builder builder = new Builder();
                for (final int num : in) {
                    final Query q = NumericRangeQuery.newIntRange(fieldName, num, num, true, true);
                    builder.add(q, Occur.SHOULD);
                }
                return builder.build();
            }
        }

        return null;
    }

    private Query getLongIn(final String fieldName, final String value) {
        final long[] in = getLongs(fieldName, value);
        if (in.length > 0) {
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

    private Query getFloatIn(final String fieldName, final String value) {
        final float[] in = getFloats(fieldName, value);
        if (in.length > 0) {
            if (in.length == 1) {
                final float num = in[0];
                return NumericRangeQuery.newFloatRange(fieldName, num, num, true, true);
            } else {
                final Builder builder = new Builder();
                for (final float num : in) {
                    final Query q = NumericRangeQuery.newFloatRange(fieldName, num, num, true, true);
                    builder.add(q, Occur.SHOULD);
                }
                return builder.build();
            }
        }

        return null;
    }

    private Query getDoubleIn(final String fieldName, final String value) {
        final double[] in = getDoubles(fieldName, value);
        if (in.length > 0) {
            if (in.length == 1) {
                final double num = in[0];
                return NumericRangeQuery.newDoubleRange(fieldName, num, num, true, true);
            } else {
                final Builder builder = new Builder();
                for (final double num : in) {
                    final Query q = NumericRangeQuery.newDoubleRange(fieldName, num, num, true, true);
                    builder.add(q, Occur.SHOULD);
                }
                return builder.build();
            }
        }

        return null;
    }

    private Query getDateIn(final String fieldName, final String value) {
        final long[] in = getDates(fieldName, value);
        if (in.length > 0) {
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

    private Query getContains(final String fieldName,
                              final String value,
                              final IndexField indexField,
                              final Set<String> terms) {
        final Query query = getSubQuery(indexField, value, terms);
        return modifyOccurrence(query, Occur.MUST);
    }

    private Query getIn(final String fieldName,
                        final String value,
                        final IndexField indexField,
                        final Set<String> terms) {
        final Builder orTermsBuilder = new Builder();
        InTermsUtil.getInTerms(value).forEach(val ->
                orTermsBuilder.add(getSubQuery(indexField, val, terms), Occur.SHOULD));
        return orTermsBuilder.build();
    }

    private Query modifyOccurrence(final Query query, final Occur occur) {
        // Change all occurs to must as we want to insist that all terms exist
        // in the matched documents.
        if (query instanceof final BooleanQuery bq) {
            final Builder builder = new Builder();
            for (final BooleanClause bc : bq.clauses()) {
                builder.add(bc.getQuery(), occur);
            }
            return builder.build();
        }
        return query;
    }

    private Query getDictionary(final String fieldName,
                                final DocRef docRef,
                                final IndexField indexField,
                                final Set<String> terms) {
        final String[] wordArr = loadWords(docRef);
        final Builder builder = new Builder();
        for (final String val : wordArr) {
            Query query;

            if (FieldType.INTEGER.equals(indexField.getFldType())) {
                query = getIntIn(fieldName, val);
            } else if (FieldType.LONG.equals(indexField.getFldType())) {
                query = getLongIn(fieldName, val);
            } else if (FieldType.FLOAT.equals(indexField.getFldType())) {
                query = getFloatIn(fieldName, val);
            } else if (FieldType.DOUBLE.equals(indexField.getFldType())) {
                query = getDoubleIn(fieldName, val);
            } else if (FieldType.DATE.equals(indexField.getFldType())) {
                query = getDateIn(fieldName, val);
            } else if (indexField.getFldType().isNumeric()) {
                query = getLongIn(fieldName, val);
            } else {
                query = getSubQuery(indexField, val, terms);
            }

            if (query != null) {
                // DictionaryDocument terms on one line must all exist in the
                // matching documents so change to must.
                query = modifyOccurrence(query, Occur.MUST);
                builder.add(query, Occur.SHOULD);
            }
        }
        return builder.build();
    }

    private String[] loadWords(final DocRef docRef) {
        final String[] words = wordListProvider.getWords(docRef);
        if (words == null) {
            throw new SearchException("Dictionary \"" + docRef + "\" not found");
        }

        return words;
    }

    private Occur getOccur(final ExpressionOperator operator) {
        return switch (operator.op()) {
            case OR -> Occur.SHOULD;
            case NOT -> Occur.MUST_NOT;
            default -> Occur.MUST;
        };
    }

    private Query getSubQuery(final IndexField field,
                              final String value,
                              final Set<String> terms) {
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
        if (!AnalyzerType.KEYWORD.equals(field.getAnalyzerType())) {
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

            if (!val.isEmpty()) {
                final StandardQueryParser queryParser = new StandardQueryParser(analyzer);
                queryParser.setAllowLeadingWildcard(true);
                queryParser.setLowercaseExpandedTerms(!field.isCaseSensitive());

                try {
                    query = queryParser.parse(val, field.getFldName());
                } catch (final QueryNodeException e) {
                    throw new SearchException("Unable to parse query term '" + val + "'", e);
                }
            }

        } else {
            if (!val.isEmpty()) {
                // As this is just indexed as a keyword we only want to search
                // for the term.
                if (!field.isCaseSensitive()) {
                    val = val.toLowerCase();
                }

                final Term term = new Term(field.getFldName(), val);
                final boolean termContainsWildcard = (val.indexOf('*') != -1) || (val.indexOf('?') != -1);
                if (termContainsWildcard) {
                    query = new WildcardQuery(new Term(field.getFldName(), val));
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
                    if (child instanceof final ExpressionOperator childOperator) {
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

    private long[] getDates(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final long[] dates = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            dates[i] = DateExpressionParser.getMs(fieldName, values[i].trim(), dateTimeSettings);
        }

        return dates;
    }

    private int getInt(final String fieldName, final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw new SearchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private int[] getInts(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final int[] numbers = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getInt(fieldName, values[i].trim());
        }

        return numbers;
    }

    private long getLong(final String fieldName, final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new SearchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private long[] getLongs(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final long[] numbers = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getLong(fieldName, values[i].trim());
        }

        return numbers;
    }

    private float getFloat(final String fieldName, final String value) {
        try {
            return Float.parseFloat(value);
        } catch (final NumberFormatException e) {
            throw new SearchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private float[] getFloats(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final float[] numbers = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getFloat(fieldName, values[i].trim());
        }

        return numbers;
    }

    private double getDouble(final String fieldName, final String value) {
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException e) {
            throw new SearchException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\"");
        }
    }

    private double[] getDoubles(final String fieldName, final String value) {
        final String[] values = value.split(DELIMITER);
        final double[] numbers = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            numbers[i] = getDouble(fieldName, values[i].trim());
        }

        return numbers;
    }

    public static class SearchExpressionQuery {

        private final Query query;
        private final Set<String> terms;

        SearchExpressionQuery(final Query query, final Set<String> terms) {
            this.query = query;
            this.terms = terms;
        }

        public Query getQuery() {
            return query;
        }

        Set<String> getTerms() {
            return terms;
        }

        @Override
        public String toString() {
            return query.toString();
        }
    }
}
