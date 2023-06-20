package stroom.dashboard.impl;

import stroom.dashboard.shared.StructureElement;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class StructureElementServiceImpl implements StructureElementService {

    private final List<StructureElement> list = new ArrayList<>();

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:RegexpSingleline"})
    @Inject
    StructureElementServiceImpl() {
        add("from",
                """
                        Select the data source to query, e.g.
                        `from my_source`
                                            
                        If the name of the data source contains whitespace then it must be quoted, e.g.
                        `from "my source"`
                        """);
        add("where",
                """
                        Use where to construct query criteria, e.g.
                        `where feed = "my feed"`
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g.
                        `where feed = "my feed" or feed = "other feed"`
                                            
                        Use brackets to group logical sub expressions, e.g.
                        `where user = "bob" and (feed = "my feed" or feed = "other feed")`
                        """);
        add("filter",
                """
                        Use filter to filter values that have not been indexed during search retrieval.
                        This is used the same way as the `where` clause but applies to data after being retrieved from the index, e.g.
                        `filter obscure_field = "some value"`
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria as supported by the `where` clause.
                        Use brackets to group logical sub expressions as supported by the `where` clause.
                        """);
        add("eval",
                """
                        Use eval to apply a function and get a result, e.g.
                        `eval my_count = count()`
                                            
                        Here the result of the `count()` function is being stored in a variable called `my_count`.
                        Functions can be nested and applied to variables, e.g.
                        `eval new_name = concat(substring(name, 3, 5), substring(name, 8, 9))`.
                                            
                        Note that all fields in the data source selected using `from` will be available as variables by default.
                                            
                        Multiple `eval` statements can also be used to breakup complex function expressions and make it easier to comment out individual evaluations, e.g.
                        `eval name_prefix = substring(name, 3, 5)`
                        `eval name_suffix = substring(name, 8, 9)`
                        `eval new_name = concat(name_prefix, name_suffix)`
                                            
                        Variables can be reused, e.g.
                        `eval name_prefix = substring(name, 3, 5)`
                        `eval new_name = substring(name, 8, 9)`
                        `eval new_name = concat(name_prefix, new_name)`
                                            
                        Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g. `where feed = "my feed" or feed = "other feed"`.
                        Use brackets to group logical sub expressions, e.g. `where user = "bob" and (feed = "my feed" or feed = "other feed")`.
                        """);
        add("group by",
                """
                        Use to group by columns, e.g.
                        `group by feed`
                                            
                        You can group across multiple columns, e.g.
                        `group by feed, name`
                                            
                        You can create nested groups, e.g.
                        `group by feed`
                        `group by name`
                        """);
        add("sort by",
                """
                        Use to sort by columns, e.g.
                        `sort by feed`
                                            
                        You can sort across multiple columns, e.g.
                        `sort by feed, name`
                                            
                        You can change the sort direction, e.g.
                        `sort by feed asc`
                        or
                        `sort by feed desc`
                        """);
        add("select",
                """
                        Select the columns to display in the table output, e.g.
                        `select feed, name`
                                            
                        You can choose the column names, e.g.
                        `select feed as 'my feed column', name as 'my name column'`
                        """);
        add("limit",
                """
                        Limit the number of results, e.g.
                        `limit 10`
                        """);
        add("window",
                """
                        Create windowed data, e.g.
                        `window EventTime by 1y`
                        This will create counts for grouped rows per year plus the previous year.
                                          
                        `window EventTime by 1y advance 1m`
                        This will create counts for grouped rows for each year long period every month and will include the previous 12 months.
                        """);
        add("having",
                """
                        Apply a post aggregate filter to data, e.g.
                        `having count > 3`
                        """);
    }

    private void add(final String title, final String detail) {
        list.add(new StructureElement(title, detail));
    }

    @Override
    public List<StructureElement> getStructureElements() {
        return list;
    }
}
