package stroom.query.impl;

import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpStructureElement;
import stroom.query.shared.QueryHelpTitle;
import stroom.query.shared.QueryHelpType;
import stroom.util.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage.ResultConsumer;
import stroom.util.string.StringMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class Structures {

    private static final String SECTION_ID = "structure";

    private final QueryHelpRow root;
    private final List<QueryHelpRow> list = new ArrayList<>();

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:RegexpSingleline"})
    @Inject
    Structures() {
        root = QueryHelpRow
                .builder()
                .type(QueryHelpType.TITLE)
                .id(SECTION_ID)
                .hasChildren(true)
                .title("Structure")
                .data(new QueryHelpTitle("A list of the keywords available in the Stroom Query Language."))
                .build();

        // Note 'stroomql' is not a thing, but using it in the hope that we create a language definition
        // for prismjs so that it later works. Prism will just treat it as text.
        add(list, "from",
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
        add(list, "where",
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
        add(list, "filter",
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
        add(list, "eval",
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
        add(list, "group by",
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
        add(list, "sort by",
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
        add(list, "select",
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
        add(list, "limit",
                """
                        Limit the number of results, e.g.
                        ```stroomql
                        limit 10
                        ```
                        """,
                "limit ${1:count}\n$0");
        add(list, "window",
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
        add(list, "having",
                """
                        Apply a post aggregate filter to data, e.g.
                        ```stroomql
                        having count > 3
                        ```
                        """,
                "having ${1:field} ${2:=} ${3:value}\n$0");
    }

    private void add(final List<QueryHelpRow> list, final String title, final String detail, final String... snippets) {
        list.add(QueryHelpRow
                .builder()
                .type(QueryHelpType.STRUCTURE)
                .id(SECTION_ID + "." + title)
                .title(title)
                .data(new QueryHelpStructureElement(detail,
                        NullSafe.asList(snippets)))
                .build());
    }

    public void addRows(final PageRequest pageRequest,
                        final String parentPath,
                        final StringMatcher stringMatcher,
                        final ResultConsumer<QueryHelpRow> resultConsumer) {
        if (parentPath.isBlank()) {
            final boolean hasChildren = hasChildren(stringMatcher);
            resultConsumer.add(root.copy().hasChildren(hasChildren).build());
        } else if (parentPath.startsWith(SECTION_ID + ".")) {
            final ResultPageBuilder<QueryHelpRow> builder =
                    new ResultPageBuilder<>(pageRequest, Comparator.comparing(QueryHelpRow::getTitle));
            for (final QueryHelpRow row : list) {
                if (stringMatcher.match(row.getTitle()).isPresent()) {
                    builder.add(row);
                }
            }
            for (final QueryHelpRow row : builder.build().getValues()) {
                if (!resultConsumer.add(row)) {
                    break;
                }
            }
        }
    }

    private boolean hasChildren(final StringMatcher stringMatcher) {
        for (final QueryHelpRow row : list) {
            if (stringMatcher.match(row.getTitle()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
