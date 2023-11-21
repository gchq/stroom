package stroom.dashboard.impl;

import stroom.dashboard.shared.StructureElement;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
class StructureElementServiceImpl implements StructureElementService {

    private final List<StructureElement> list = new ArrayList<>();

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:RegexpSingleline"})
    @Inject
    StructureElementServiceImpl() {

        // Note 'stroomql' is not a thing, but using it in the hope that we create a language definition
        // for prismjs so that it later works. Prism will just treat it as text.
        add("from",
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
        add("where",
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
        add("filter",
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
        add("eval",
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
        add("group by",
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
        add("sort by",
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
        add("select",
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
        add("limit",
                """
                        Limit the number of results, e.g.
                        ```stroomql
                        limit 10
                        ```
                        """,
                "limit ${1:count}\n$0");
        add("window",
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
        add("having",
                """
                        Apply a post aggregate filter to data, e.g.
                        ```stroomql
                        having count > 3
                        ```
                        """,
                "having ${1:field} ${2:=} ${3:value}\n$0");
    }

    private void add(final String title, final String detail) {
        list.add(new StructureElement(title, detail, Collections.emptyList()));
    }

    private void add(final String title, final String detail, final String... snippets) {
        list.add(new StructureElement(title, detail, NullSafe.asList(snippets)));
    }

    @Override
    public List<StructureElement> getStructureElements() {
        return list;
    }
}
