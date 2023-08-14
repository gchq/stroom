package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.StructureElement;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.client.presenter.QueryHelpPresenter.InsertType;
import stroom.query.shared.QueryResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
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


    private List<StructureQueryHelpItem> orphanedItems;
    private ThemeType themeType = null;


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

    public void fetchStructureElements(
            final QueryHelpItem parent,
            final Consumer<List<StructureQueryHelpItem>> consumer) {
        // Theme is baked into the html due to the way prism works, so we need to rebuild
        // if the theme has changed
        if (GwtNullSafe.hasItems(orphanedItems) && !hasThemeChanged()) {
            // Build a new list of items attaching each to the new parent
            consumer.accept(cloneWithNewParent(parent, orphanedItems));
        } else {
            fetchHelpUrl(helpUrl -> {
                final Rest<List<StructureElement>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            orphanedItems = result
                                    .stream()
                                    .map(structureElement -> {
                                        final SafeHtml detailHtml = buildDescriptionHtml(
                                                structureElement.getDescription());

                                        // No parent at this point as we want to hold these
                                        // items for other many callers to re-use
                                        return new StructureQueryHelpItem(
                                                null,
                                                structureElement.getTitle(),
                                                detailHtml,
                                                structureElement.getSnippets(),
                                                helpUrl);
                                    })
                                    .collect(Collectors.toList());
                            // Not the theme type in use when we built, so we can see if a rebuild is needed
                            // in future
                            themeType = markdownConverter.geCurrentThemeType();
                            consumer.accept(cloneWithNewParent(parent, orphanedItems));
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

    private List<StructureQueryHelpItem> cloneWithNewParent(final QueryHelpItem parent,
                                                      final List<StructureQueryHelpItem> items) {
        return GwtNullSafe.stream(items)
                .map(item -> item.withNewParent(parent))
                .collect(Collectors.toList());
    }

    private SafeHtml buildDescriptionHtml(final String description) {
        return markdownConverter.convertMarkdownToHtml(description);
    }

    private boolean hasThemeChanged() {
        final ThemeType themeType = markdownConverter.geCurrentThemeType();
        return !Objects.equals(themeType, this.themeType);
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


    // --------------------------------------------------------------------------------


    public static class StructureQueryHelpItem extends QueryHelpItem {

        private final SafeHtml detail;
        private final List<String> snippets;

        public StructureQueryHelpItem(final QueryHelpItem parent,
                                      final String title,
                                      final SafeHtml detail,
                                      final List<String> snippets,
                                      final String helpUrl) {
            super(parent, title, false);
            this.detail = buildDetailHtml(title, detail, helpUrl);
            this.snippets = new ArrayList<>(snippets);
        }

        private StructureQueryHelpItem(final QueryHelpItem parent,
                                      final String title,
                                      final SafeHtml detail,
                                      final List<String> snippets) {
            super(parent, title, false);
            this.detail = detail;
            this.snippets = snippets;
        }

        private static SafeHtml buildDetailHtml(final String title,
                                                final SafeHtml detail,
                                                final String helpUrl) {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(title));
                hb1.br();
                hb1.hr();

                hb1.para(hb2 -> hb2.append(detail),
                        Attribute.className("queryHelpDetail-description"));

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
            }, Attribute.className("queryHelpDetail"));
            return htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public InsertType getInsertType() {
            return GwtNullSafe.hasItems(snippets)
                    ? InsertType.SNIPPET
                    : InsertType.BLANK;
        }

        @Override
        String getClassName() {
            return super.getClassName() + " queryHelpItem-leaf";
        }

        @Override
        public String getInsertText() {
            return GwtNullSafe.list(snippets)
                    .get(0);
        }

        public List<String> getSnippets() {
            return snippets;
        }

        public StructureQueryHelpItem withNewParent(final QueryHelpItem newParent) {
            return new StructureQueryHelpItem(newParent, title, detail, snippets);
        }
    }
}
