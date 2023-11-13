package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.shared.QueryHelpDataSource;
import stroom.query.shared.QueryHelpRow;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

public class DataSourceDetailProvider implements DetailProvider, HasHandlers {

    private static final String DETAIL_BASE_CLASS = "queryHelpDetail";
    private static final String DETAIL_DESCRIPTION_CLASS = DETAIL_BASE_CLASS + "-description";

    private static final String DETAIL_TABLE_CLASS = DETAIL_BASE_CLASS + "-table";
    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);

    private final EventBus eventBus;
    private final MarkdownConverter markdownConverter;
    private final RestFactory restFactory;

    @Inject
    public DataSourceDetailProvider(final EventBus eventBus,
                                    final MarkdownConverter markdownConverter,
                                    final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;
    }

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        final QueryHelpDataSource dataSource = (QueryHelpDataSource) row.getData();
        // Special case for dataSource items to lazily load the markdown documentation.
        // This is the name/type/uuid of the docref as a minimum
        final SafeHtml detailSafeHtml = createDataSourceHtml(dataSource.getDocRef());
        fetchDataSourceDescription(
                dataSource.getDocRef(),
                optMarkdown -> {
                    final InsertType insertType = GwtNullSafe.isBlankString(row.getTitle())
                            ? InsertType.BLANK
                            : InsertType.PLAIN_TEXT;
                    final String insertText = GwtNullSafe.get(row.getTitle(), title2 ->
                            title2.contains(" ")
                                    ? "\"" + title2 + "\""
                                    : title2);
                    final SafeHtml documentation = buildDatasourceDescription(
                            optMarkdown, detailSafeHtml);
                    final Detail detail = new Detail(insertType, insertText, documentation);
                    consumer.accept(detail);
                });
    }

    private SafeHtml createDataSourceHtml(final DocRef docRef) {
        final HtmlBuilder docRefBuilder = HtmlBuilder.builder();
        appendKeyValueTable(docRefBuilder,
                Arrays.asList(
                        new SimpleEntry<>("Name:", docRef.getName()),
                        new SimpleEntry<>("Type:", docRef.getType()),
                        new SimpleEntry<>("UUID:", docRef.getUuid())));

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(htmlBuilder2 -> {
            htmlBuilder2.bold(htmlBuilder3 -> htmlBuilder3.append(docRef.getName()));
            htmlBuilder2.br();
            htmlBuilder2.hr();

            htmlBuilder2.para(htmlBuilder3 -> htmlBuilder3.append(docRefBuilder.toSafeHtml()),
                    Attribute.className(DETAIL_DESCRIPTION_CLASS));

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

//                if (helpUrlBase != null) {
//                    hb1.append("For more information see the ");
//                    hb1.appendLink(
//                            helpUrlBase +
//                                    "/user-guide/stroom-query-language/structure/" +
//                                    title.toLowerCase().replace(" ", "-") +
//                                    "#" +
//                                    title,
//                            "Help Documentation");
//                    hb1.append(".");
//                }
        }, Attribute.className(DETAIL_BASE_CLASS));

        return htmlBuilder.toSafeHtml();
    }

    private void appendKeyValueTable(final HtmlBuilder htmlBuilder,
                                     final List<Entry<String, String>> entries) {

        final TableBuilder tableBuilder = new TableBuilder();
        for (final Entry<String, String> entry : entries) {
            tableBuilder.row(
                    HtmlBuilder.builder()
                            .bold(htmlBuilder2 -> htmlBuilder2.append(entry.getKey()))
                            .toSafeHtml(),
                    SafeHtmlUtil.from(entry.getValue()));
        }
        htmlBuilder.div(tableBuilder::write, Attribute.className(DETAIL_TABLE_CLASS));
    }

    public void fetchDataSourceDescription(final DocRef dataSourceDocRef,
                                           final Consumer<Optional<String>> descriptionConsumer) {

        if (dataSourceDocRef != null) {
            final Rest<Documentation> rest = restFactory.create();
            rest.onSuccess(documentation -> {
//                                GWT.log("Description:\n" + documentation);
                        final Optional<String> optMarkDown = GwtNullSafe.getAsOptional(documentation,
                                Documentation::getMarkdown);
                        if (descriptionConsumer != null) {
                            descriptionConsumer.accept(optMarkDown);
                        }
                    })
                    .onFailure(throwable -> AlertEvent.fireError(
                            DataSourceDetailProvider.this,
                            throwable.getMessage(),
                            null))
                    .call(DATA_SOURCE_RESOURCE)
                    .fetchDocumentation(dataSourceDocRef);
        }
    }

    private SafeHtml buildDatasourceDescription(final Optional<String> optMarkdown,
                                                final SafeHtml basicDescription) {
        final SafeHtml combinedSafeHtml = optMarkdown.filter(markdown ->
                        !GwtNullSafe.isBlankString(markdown))
                .map(markdown -> {
                    final SafeHtml markDownSafeHtml = markdownConverter.convertMarkdownToHtmlInFrame(
                            markdown);
                    final SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder();
                    return safeHtmlBuilder.append(basicDescription)
                            .append(markDownSafeHtml)
                            .toSafeHtml();
                })
                .orElse(basicDescription);
        return combinedSafeHtml;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
