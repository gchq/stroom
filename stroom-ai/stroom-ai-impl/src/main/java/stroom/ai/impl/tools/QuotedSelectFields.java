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
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.query.impl.QueryService;
import stroom.query.language.token.Tokeniser;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/// A field named in a StroomQL `select` in double quotes: StroomQL reads `"Create Time"` there as that text, given in
/// every row, not as the field, so the query runs without an error and gives a column of the field's name. A field
/// whose name has a space is written `${Create Time}`; double quotes give text, or a column name after `as`.
/// (In `where`, Stroom does take a double-quoted name as a field.) validate_stroomql and run_stroomql say so.
final class QuotedSelectFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QuotedSelectFields.class);

    private static final Set<TokenType> SKIPPED = Set.of(TokenType.WHITESPACE, TokenType.COMMENT,
            TokenType.BLOCK_COMMENT);

    private QuotedSelectFields() {
    }

    /// The double-quoted texts that stand as a whole column of a query's `select` (not a column name after `as`,
    /// nor a function's argument).
    ///
    /// @param query A StroomQL query.
    /// @return The texts, unescaped, in the order written; none if the query can't be read.
    static List<String> quotedColumns(final String query) {
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query).stream()
                    .filter(token -> !SKIPPED.contains(token.getTokenType()))
                    .toList();
        } catch (final RuntimeException e) {
            // The parser's own error is Stroom's to give.
            LOGGER.debug(() -> LogUtil.message("Unable to read the query's select: {}", e.getMessage()), e);
            return List.of();
        }
        final List<String> quoted = new ArrayList<>();
        boolean inSelect = false;
        for (int i = 0; i < tokens.size(); i++) {
            final TokenType type = tokens.get(i).getTokenType();
            if (TokenType.ALL_KEYWORDS.contains(type)) {
                inSelect = TokenType.SELECT.equals(type);
            } else if (inSelect && TokenType.DOUBLE_QUOTED_STRING.equals(type)) {
                final TokenType before = tokens.get(i - 1).getTokenType();
                final TokenType after = i + 1 < tokens.size()
                        ? tokens.get(i + 1).getTokenType()
                        : null;
                if ((TokenType.SELECT.equals(before) || TokenType.COMMA.equals(before))
                    && (after == null || TokenType.COMMA.equals(after) || TokenType.AS.equals(after)
                        || TokenType.ALL_KEYWORDS.contains(after))) {
                    quoted.add(tokens.get(i).getUnescapedText());
                }
            }
        }
        return quoted;
    }

    /// What a result says of the fields a query's `select` names in double quotes.
    ///
    /// @param query  A StroomQL query.
    /// @param fields The names of its data source's fields.
    /// @return The note, starting with a space, or nothing if no column is a field's name in double quotes.
    static String note(final String query, final Collection<String> fields) {
        final Map<String, String> byLowerCase = fields.stream()
                .filter(NullSafe::isNonBlankString)
                .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), Function.identity(),
                        (first, second) -> first));
        final List<String> named = quotedColumns(query).stream()
                .map(text -> byLowerCase.get(text.toLowerCase(Locale.ROOT)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (named.isEmpty()) {
            return "";
        }
        return " In select, " + named.stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "))
               + (named.size() == 1
                ? " is that text in every row, not the field"
                : " are those texts in every row, not the fields")
               + ": write " + named.stream()
                       .map(name -> "${" + name + "}")
                       .collect(Collectors.joining(", "))
               + " (a field whose name has a space is written ${...}; double quotes give text, or a column name "
               + "after as).";
    }

    /// What a result says of the fields a query's `select` names in double quotes, the field names read from its
    /// data source.
    ///
    /// @param queryService Lists the data source's fields.
    /// @param dataSource   The data source the query's `from` names, or null.
    /// @param query        The query.
    /// @return The note, or nothing if there is none or the fields can't be read.
    static String note(final QueryService queryService, final DocRef dataSource, final String query) {
        if (dataSource == null || quotedColumns(query).isEmpty()) {
            return "";
        }
        try {
            final List<QueryField> fields = NullSafe.list(NullSafe.get(queryService.findFields(new FindFieldCriteria(
                    new PageRequest(0, IndexEvents.MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource,
                    null, null)), ResultPage::getValues));
            return note(query, fields.stream().map(QueryField::getFldName).toList());
        } catch (final RuntimeException e) {
            // A note on the query, not a reason for it not to run.
            LOGGER.debug(() -> LogUtil.message("Unable to read the data source's fields: {}", e.getMessage()), e);
            return "";
        }
    }
}
