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

package stroom.query.impl;

import stroom.query.api.token.TokenType;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionSnippet;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.ui.config.shared.UiConfig;
import stroom.util.collections.TrimmedSortedList;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.string.AceStringMatcher;
import stroom.util.string.AceStringMatcher.AceMatchResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
class Structures {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Structures.class);

    private static final String SECTION_ID = "structure";
    public static final int INITIAL_SCORE = 400;

    private final Provider<UiConfig> uiConfigProvider;
    private final QueryHelpRow root;
    private final List<QueryHelpRow> list = new ArrayList<>();
    private final Map<String, StructureElement> map = new HashMap<>();

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:RegexpSingleline"})
    @Inject
    Structures(final Provider<UiConfig> uiConfigProvider) {
        this.uiConfigProvider = uiConfigProvider;

        root = QueryHelpRow
                .builder()
                .type(QueryHelpType.TITLE)
                .id(SECTION_ID)
                .hasChildren(true)
                .title("Structure")
                .build();

        // Note 'stroomql' is not a thing in our markdown, but using it in the hope that we create a
        // language definition for prismjs so that it later works. Prism will just treat it as text.
        // Ideally have these in the order they appear in a stroomQL statement
        add(list, name(TokenType.FROM),
                """
                        Select the data source to query, e.g.
                        ```stroomql
                        from my_source
                        ```
                                            
                        If the name of the data source contains whitespace then it must be quoted, e.g.
                        ```stroomql
                        from "my source"
                        ```
                        """,
                "from \"${1:view}\"$0");
        add(list, name(TokenType.WHERE),
                """
                        Use where to construct query criteria, e.g.
                        ```stroomql
                        where feed = "my feed"
                        ```
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g.
                        ```stroomql
                        where feed = "my feed"
                        or feed = "other feed"
                        ```
                                            
                        Use brackets to group logical sub expressions, e.g.
                        ```stroomql
                        where user = "bob"
                        and (feed = "my feed" or feed = "other feed")
                        ```
                        """,
                "where ${1:field} ${2:=} ${3:value}\n$0");
        add(list, name(TokenType.EVAL),
                """
                        Use eval to apply a function and get a result, e.g.
                        ```stroomql
                        eval my_count = count()
                        ```
                                            
                        Here the result of the `count()` function is being stored in a variable called `my_count`.
                        Functions can be nested and applied to variables, e.g.
                        ```stroomql
                        eval new_name = concat(
                          substring(name, 3, 5),
                          substring(name, 8, 9))
                        ```
                                            
                        Note that all fields in the data source selected using `from` will be available as variables by default.
                                            
                        Multiple `eval` statements can also be used to breakup complex function expressions and make it easier to comment out individual evaluations, e.g.
                        ```stroomql
                        eval name_prefix = substring(name, 3, 5)
                        eval name_suffix = substring(name, 8, 9)
                        eval new_name = concat(
                          name_prefix,
                          name_suffix)
                        ```
                                            
                        Variables can be reused, e.g.
                        ```stroomql
                        eval name_prefix = substring(name, 3, 5)
                        eval new_name = substring(name, 8, 9)
                        eval new_name = concat(
                          name_prefix,
                          new_name)
                        ```
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g.
                        ```stroomql
                        where feed = "my feed"
                        or feed = "other feed"
                        ```
                                                
                        Use brackets to group logical sub expressions, e.g.
                                                
                        ```stroomsql
                        where user = "bob"
                        and (feed = "my feed" or feed = "other feed")
                        ```
                        """,
                "eval ${1:variable_name} = ${2:value}\n$0");
        add(list, name(TokenType.WINDOW),
                """
                        Create windowed data, e.g.
                        ```stroomql
                        window EventTime by 1y
                        ```
                        This will create counts for grouped rows per year plus the previous year.
                                          
                        ```stroomql
                        window EventTime by 1y advance 1m
                        ```
                        This will create counts for grouped rows for each year long period every month and will include the previous 12 months.
                        """,
                "window ${1:field} by ${2:period} advance ${3:adv_value}\n$0");
        add(list, name(TokenType.FILTER),
                """
                        Use filter to filter values that have not been indexed during search retrieval.
                        This is used the same way as the `where` clause but applies to data after being retrieved from the index, e.g.
                        ```stroomql
                        filter obscure_field = "some value"
                        ```
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria as supported by the `where` clause.
                        Use brackets to group logical sub expressions as supported by the `where` clause.
                        """,
                "filter ${1:field} ${2:=} ${3:value}\n$0");
        add(list, name(TokenType.SORT, TokenType.BY),
                """
                        Use to sort by columns, e.g.
                        ```stroomql
                        sort by feed
                        ```
                                            
                        You can sort across multiple columns, e.g.
                        ```stroomql
                        sort by feed, name
                        ```
                                            
                        You can change the sort direction, e.g.
                        ```stroomql
                        sort by feed asc
                        ```
                        or
                        ```stroomql
                        sort by feed desc
                        ```
                        """,
                "sort by ${1:field(s)}$0");
        add(list, name(TokenType.GROUP, TokenType.BY),
                """
                        Use to group by columns, e.g.
                        ```stroomql
                        group by feed
                        ```
                                            
                        You can group across multiple columns, e.g.
                        ```stroomql
                        group by feed, name
                        ```
                                            
                        You can create nested groups, e.g.
                        ```stroomql
                        group by feed
                        group by name
                        ```
                        """,
                "group by ${1:field(s)}$0");
        add(list, name(TokenType.HAVING),
                """
                        Apply a post aggregate filter to data, e.g.
                        ```stroomql
                        having count > 3
                        ```
                        """,
                "having ${1:field} ${2:=} ${3:value}\n$0");
        add(list, name(TokenType.LIMIT),
                """
                        Limit the number of results, e.g.
                        ```stroomql
                        limit 10
                        ```
                        """,
                "limit ${1:count}\n$0");
        add(list, name(TokenType.SELECT),
                """
                        Select the columns to display in the table output, e.g.
                        ```stroomql
                        select feed, name
                        ```
                                            
                        You can choose the column names, e.g.
                        ```stroomql
                        select feed as 'my feed column',
                          name as 'my name column'
                        ```
                        """,
                "select ${1:field(s)}$0");
        add(list, name(TokenType.SHOW),
                """
                        Visualise the selected data using a Stroom visualisation, e.g.
                        ```stroomql
                        show LineChart(x = EventTime, y = count)
                        show Doughnut(names = Feed, values = count)
                        ```
                        """,
                "show ${1:visualisation}$0");
    }

    static String name(final TokenType... tokenTypes) {
        Objects.requireNonNull(tokenTypes);
        return Arrays.stream(tokenTypes)
                .map(TokenType::name)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
    }

    private void add(final List<QueryHelpRow> list,
                     final String title,
                     final String detail,
                     final String... snippets) {
        list.add(QueryHelpRow
                .builder()
                .type(QueryHelpType.STRUCTURE)
                .id(SECTION_ID + "." + title)
                .title(title)
                .build());
        map.put(title, new StructureElement(title, detail, snippets));
    }

    public void addRows(final PageRequest pageRequest,
                        final String parentPath,
                        final Predicate<String> predicate,
                        final ResultPageBuilder<QueryHelpRow> resultPageBuilder) {
        if (parentPath.isBlank()) {
            final boolean hasChildren = hasChildren(predicate);
            if (hasChildren ||
                predicate.test(root.getTitle())) {
                resultPageBuilder.add(root.copy().hasChildren(hasChildren).build());
            }
        } else if (parentPath.startsWith(SECTION_ID + ".")) {
            final TrimmedSortedList<QueryHelpRow> trimmedSortedList =
                    new TrimmedSortedList<>(pageRequest, Comparator.comparing(QueryHelpRow::getTitle));
            for (final QueryHelpRow row : list) {
                if (predicate.test(row.getTitle())) {
                    trimmedSortedList.add(row);
                }
            }

            final List<QueryHelpRow> list = trimmedSortedList.getList();
            for (final QueryHelpRow row : list) {
                resultPageBuilder.add(row);
            }
        }
    }

    private boolean hasChildren(final Predicate<String> predicate) {
        for (final QueryHelpRow row : list) {
            if (predicate.test(row.getTitle())) {
                return true;
            }
        }
        return false;
    }

    public void addCompletions(final CompletionsRequest request,
                               final int maxCompletions,
                               final List<CompletionItem> resultList,
                               final Set<String> applicableStructureItems) {
        try {
            // Depending on where your cursor is will govern what structure items are allowed,
            // so first filter out the invalid ones before comparing to the stringMatcher
            final List<QueryHelpRow> fullList = list.stream()
                    .filter(queryHelpRow -> {
                        if (QueryHelpType.STRUCTURE == queryHelpRow.getType()) {
                            return applicableStructureItems.contains(queryHelpRow.getTitle());
                        } else {
                            return true;
                        }
                    })
                    .toList();

            if (fullList.size() > maxCompletions) {
                // We have more than we can show, so we have to pre-filter the completions
                // which is not ideal as Ace does not re-ask for completions if the user relaxes
                // their completion prefix
                final List<AceMatchResult<QueryHelpRow>> matchResults = AceStringMatcher.filterCompletions(
                        fullList,
                        request.getPattern(),
                        INITIAL_SCORE,
                        QueryHelpRow::getTitle);
                matchResults.sort(AceStringMatcher.SCORE_DESC_THEN_NAME_COMPARATOR);

                LOGGER.debug(() -> LogUtil.message("Found {} match results, from {} items, maxCompletions {}",
                        matchResults.size(), fullList.size(), maxCompletions));

                matchResults.stream()
                        .limit(maxCompletions)
                        .map(matchResult -> createCompletionValue(matchResult.item(), matchResult.score()))
                        .forEach(resultList::add);
            } else {
                LOGGER.debug(() -> LogUtil.message("Found {} match results using offset {}, maxCompletions {}",
                        fullList.size(), maxCompletions));
                fullList.stream()
                        .map(row -> createCompletionValue(row, INITIAL_SCORE))
                        .forEach(resultList::add);
            }
        } catch (final Exception e) {
            LOGGER.error("Error adding structure completions: {}", e.getMessage(), e);
        }
    }

    private CompletionSnippet createCompletionValue(final QueryHelpRow row, final int score) {
        final StructureElement structureElement = map.get(row.getTitle());
        final String detail = getDetail(structureElement);
        return new CompletionSnippet(
                row.getTitle(),
                structureElement.snippets[0],
                score,
                "Structure",
                detail);
    }

    private String getDetail(final StructureElement structureElement) {
        final DetailBuilder detail = new DetailBuilder();
        detail.title(structureElement.title);
        detail.description(description -> description.append(structureElement.detail));

        final UiConfig uiConfig = uiConfigProvider.get();
        if (uiConfig.getHelpUrl() != null && uiConfig.getHelpSubPathStroomQueryLanguage() != null) {
            detail.append("For more information see the ");
            detail.appendLink(
                    uiConfig.getHelpUrl() +
                    uiConfig.getHelpSubPathStroomQueryLanguage() +
                    "#" + structureElement.title.toLowerCase().replace(' ', '-'),
                    "Help Documentation");
            detail.append(".");
        }

        return detail.build();
    }

    public Optional<QueryHelpDetail> fetchDetail(final QueryHelpRow row) {
        if (SECTION_ID.equals(row.getId())) {
            final InsertType insertType = InsertType.NOT_INSERTABLE;
            final String documentation = "A list of the keywords available in the Stroom Query Language.";
            return Optional.of(new QueryHelpDetail(insertType, null, documentation));

        } else if (row.getId().startsWith(SECTION_ID + ".")) {
            final StructureElement structureElement = map.get(row.getTitle());
            if (structureElement == null) {
                return Optional.empty();
            }

            final List<String> snippets = NullSafe.asList(structureElement.snippets);
            final String insertText = snippets.stream().findFirst().orElse(null);
            final InsertType insertType = InsertType.snippet(insertText);
            final String documentation = getDetail(structureElement);
            return Optional.of(new QueryHelpDetail(insertType, insertText, documentation));
        }

        return Optional.empty();
    }


    // --------------------------------------------------------------------------------


    private record StructureElement(String title, String detail, String... snippets) {

    }
}
