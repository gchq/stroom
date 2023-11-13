package stroom.query.client.presenter;

import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpStructureElement;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.function.Consumer;
import javax.inject.Inject;

public class StructureDetailProvider implements DetailProvider {

    private final MarkdownConverter markdownConverter;
    private final HelpUrlProvider helpUrlProvider;

    @Inject
    public StructureDetailProvider(final MarkdownConverter markdownConverter,
                                   final HelpUrlProvider helpUrlProvider) {
        this.markdownConverter = markdownConverter;
        this.helpUrlProvider = helpUrlProvider;
    }

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        final QueryHelpStructureElement structureElement = (QueryHelpStructureElement) row.getData();
        helpUrlProvider.fetchHelpUrl(helpUrl -> {
            final InsertType insertType = GwtNullSafe.hasItems(structureElement.getSnippets())
                    ? InsertType.SNIPPET
                    : InsertType.BLANK;
            final String insertText = GwtNullSafe.list(structureElement.getSnippets())
                    .get(0);
            final SafeHtml documentation = getDocumentation(row.getTitle(), structureElement, helpUrl);
            final Detail detail = new Detail(insertType, insertText, documentation);
            consumer.accept(detail);
        });
    }

    private SafeHtml getDocumentation(final String title,
                                      final QueryHelpStructureElement structureElement,
                                      final String helpUrl) {
        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        return htmlBuilder.div(hb1 -> {
            hb1.bold(hb2 -> hb2.append(title));
            hb1.br();
            hb1.hr();

            final SafeHtml detailHtml = markdownConverter.convertMarkdownToHtml(structureElement.getDescription());
            hb1.para(hb2 -> hb2.append(detailHtml),
                    Attribute.className("queryHelpDetail-description"));

            if (helpUrl != null) {
                hb1.append("For more information see the ");
                hb1.appendLink(
                        helpUrl + "#" + title.toLowerCase().replace(" ", "-"),
                        "Help Documentation");
                hb1.append(".");
            }
        }, Attribute.className("queryHelpDetail")).toSafeHtml();
    }
}
