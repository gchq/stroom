package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.StructureElement;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.client.presenter.QueryHelpPresenter.InsertType;
import stroom.query.shared.QueryResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("checkstyle:LineLength")
public class QueryStructure implements HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final MarkdownConverter markdownConverter;

    private List<StructureQueryHelpItem> items;
    private String cssClasses = null;


    @Inject
    QueryStructure(final EventBus eventBus,
                   final RestFactory restFactory,
                   final UiConfigCache uiConfigCache,
                   final MarkdownConverter markdownConverter) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.markdownConverter = markdownConverter;
    }

    public void fetchStructureElements(final Consumer<List<StructureQueryHelpItem>> consumer) {
        // Theme is baked into the html due to the way prism works so we need to rebuild
        // if the theme has changed
        if (items != null && !hasThemeChanged()) {
            consumer.accept(items);
        } else {
            fetchHelpUrl(helpUrl -> {
                final Rest<List<StructureElement>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            items = result
                                    .stream()
                                    .map(structureElement -> {
                                        final SafeHtml detailHtml = buildDescriptionHtml(
                                                structureElement.getDescription());
                                        return new StructureQueryHelpItem(
                                                structureElement.getTitle(),
                                                detailHtml,
                                                helpUrl);
                                    })
                                    .collect(Collectors.toList());
                            consumer.accept(items);
                        })
                        .onFailure(throwable -> AlertEvent.fireError(
                                this,
                                throwable.getMessage(),
                                null))
                        .call(QUERY_RESOURCE)
                        .fetchStructureElements();
            });
        }
    }

    private SafeHtml buildDescriptionHtml(final String description) {
        final SafeHtml markdownHtml = markdownConverter.convertMarkdownToHtml(description);
        cssClasses = markdownConverter.getMarkdownContainerClasses();

        return HtmlBuilder.builder()
                .div(builder -> builder.append(markdownHtml),
                        Attribute.className(cssClasses))
                .toSafeHtml();
    }

    private boolean hasThemeChanged() {
        final String cssClasses = markdownConverter.getMarkdownContainerClasses();
        return !Objects.equals(cssClasses, this.cssClasses);
    }

    private void fetchHelpUrl(final Consumer<String> consumer) {
        uiConfigCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrlStroomQueryLanguage();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        consumer.accept(helpUrl);
                    } else {
                        AlertEvent.fireError(
                                this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        this,
                        caught.getMessage(),
                        null));
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
//}

    //
//
//
//
//    private StructureDescriptions structureDescriptions;
//
//    List<QueryHelpItem> get(final String helpUrlBase) {
//        StructureDescriptions structureDescriptions = this.structureDescriptions;
//        if (structureDescriptions == null) {
//            structureDescriptions = new StructureDescriptions(helpUrlBase);
//            this.structureDescriptions = structureDescriptions;
//        }
//        return structureDescriptions.list;
//    }
//
//    private static class StructureDescriptions {
//
//        private final List<QueryHelpItem> list = new ArrayList<>();
//        private final String helpUrlBase;
//
//        public StructureDescriptions(final String helpUrlBase) {
//            this.helpUrlBase = helpUrlBase;
//            add("from",
//                    """
//                            Select the data source to query, e.g.
//                            `from my_source`
//
//                            If the name of the data source contains whitespace then it must be quoted, e.g.
//                            `from "my source"`
//                            """);
//            add("where",
//                    """
//                            Use where to construct query criteria, e.g.
//                            `where feed = "my feed"`
//
//                            Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g.
//                            `where feed = "my feed" or feed = "other feed"`
//
//                            Use brackets to group logical sub expressions, e.g.
//                            `where user = "bob" and (feed = "my feed" or feed = "other feed")`
//                            """);
//            add("filter",
//                    """
//                            Use filter to filter values that have not been indexed during search retrieval.
//                            This is used the same way as the `where` clause but applies to data after being retrieved from the index, e.g.
//                            `filter obscure_field = "some value"`
//
//                            Add boolean logic with `and`, `or` and `not` to build complex criteria as supported by the `where` clause.
//                            Use brackets to group logical sub expressions as supported by the `where` clause.
//                            """);
//            add("eval",
//                    """
//                            Use eval to apply a function and get a result, e.g.
//                            `eval my_count = count()`
//
//                            Here the result of the `count()` function is being stored in a variable called `my_count`.
//                            Functions can be nested and applied to variables, e.g.
//                            `eval new_name = concat(substring(name, 3, 5), substring(name, 8, 9))`.
//
//                            Note that all fields in the data source selected using `from` will be available as variables by default.
//
//                            Multiple `eval` statements can also be used to breakup complex function expressions and make it easier to comment out individual evaluations, e.g.
//                            `eval name_prefix = substring(name, 3, 5)`
//                            `eval name_suffix = substring(name, 8, 9)`
//                            `eval new_name = concat(name_prefix, name_suffix)`
//
//                            Variables can be reused, e.g.
//                            `eval name_prefix = substring(name, 3, 5)`
//                            `eval new_name = substring(name, 8, 9)`
//                            `eval new_name = concat(name_prefix, new_name)`
//
//                            Add boolean logic with `and`, `or` and `not` to build complex criteria, e.g. `where feed = "my feed" or feed = "other feed"`.
//                            Use brackets to group logical sub expressions, e.g. `where user = "bob" and (feed = "my feed" or feed = "other feed")`.
//                            """);
//            add("group by",
//                    """
//                            Use to group by columns, e.g.
//                            `group by feed`
//
//                            You can group across multiple columns, e.g.
//                            `group by feed, name`
//
//                            You can create nested groups, e.g.
//                            `group by feed`
//                            `group by name`
//                            """);
//            add("sort by",
//                    """
//                            Use to sort by columns, e.g.
//                            `sort by feed`
//
//                            You can sort across multiple columns, e.g.
//                            `sort by feed, name`
//
//                            You can change the sort direction, e.g.
//                            `sort by feed asc`
//                            or
//                            `sort by feed desc`
//                            """);
//            add("select",
//                    """
//                            Select the columns to display in the table output, e.g.
//                            `select feed, name`
//
//                            You can choose the column names, e.g.
//                            `select feed as 'my feed column', name as 'my name column'`
//                            """);
//            add("limit",
//                    """
//                            Limit the number of results, e.g.
//                            `limit 10`
//                            """);
//            add("window",
//                    """
//                            Create windowed data, e.g.
//                            `window EventTime by 1y`
//                            This will create counts for grouped rows per year plus the previous year.
//
//                            `window EventTime by 1y advance 1m`
//                            This will create counts for grouped rows for each year long period every month and will include the previous 12 months.
//                            """);
//            add("having",
//                    """
//                            Apply a post aggregate filter to data, e.g.
//                            `having count > 3`
//                            """);
//        }
//
//        private void add(final String title, final String detail) {
//            list.add(new StructureQueryHelpItem(title, detail, helpUrlBase));
//        }
//    }
//
//


    // --------------------------------------------------------------------------------


    public static class StructureQueryHelpItem extends QueryHelpItem {

        private final SafeHtml detail;

        public StructureQueryHelpItem(final String title, final SafeHtml detail, final String helpUrl) {
            super(title, false, 1);
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(title));
                hb1.br();
                hb1.hr();

                hb1.para(hb2 -> hb2.append(detail),
                        Attribute.className("functionSignatureInfo-description"));

//                    final boolean addedArgs = addArgsBlockToInfo(signature, hb1);
//
//                    if (addedArgs) {
//                        hb1.br();
//                    }
//
//                    final List<String> aliases = signature.getAliases();
//                    if (!aliases.isEmpty()) {
//                        hb1.para(hb2 -> hb2.append("Aliases: " +
//                                aliases.stream()
//                                        .collect(Collectors.joining(", "))));
//                    }

                if (helpUrl != null) {
                    hb1.append("For more information see the ");
                    hb1.appendLink(
                            helpUrl + "#" + title.toLowerCase().replace(" ", "-"),
                            "Help Documentation");
                    hb1.append(".");
                }
            }, Attribute.className("functionSignatureInfo"));

            this.detail = htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public InsertType getInsertType() {
            // TODO: 22/06/2023 Might want to make these snippets
            return GwtNullSafe.isBlankString(title)
                    ? InsertType.BLANK
                    : InsertType.PLAIN_TEXT;
        }

        @Override
        String getClassName() {
            return super.getClassName() + " queryHelpItem-leaf";
        }
    }
}
