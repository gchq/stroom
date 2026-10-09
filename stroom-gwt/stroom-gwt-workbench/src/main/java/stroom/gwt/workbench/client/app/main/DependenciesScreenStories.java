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


package stroom.gwt.workbench.client.app.main;

import stroom.docref.DocRef;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent.DependencyType;
import stroom.importexport.client.presenter.DependenciesTabPresenter;
import stroom.importexport.shared.DependencyCriteria;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/// Stories of `App/Main/DependenciesScreen`, showing Stroom's real [DependenciesTabPresenter] (the
/// 'Dependencies' tab) with fake REST replies.
///
/// The dependencies (`POST /content/v1/fetchDependencies`, `ContentResource.fetchDependencies`) are
/// answered by a handler that stands in for the server: it applies the criteria's `partialName`
/// (`fromuuid:`/`touuid:` or a partial name), sort and page. `onLocate`/`onProperties`/`onDelete`
/// are spies on Stroom's `LocateDocEvent`, `ShowDependenciesInfoDialogEvent` and
/// `DeleteDocumentEvent`, and 'Show dependants' (`ShowDocRefDependenciesEvent`) is handled as
/// `DependenciesPlugin` does, writing the filter into the quick filter.
public final class DependenciesScreenStories {

    /// The name of the spy recording the documents located (Stroom's `LocateDocEvent`).
    static final String ON_LOCATE = "onLocate";
    /// The name of the spy recording the documents whose properties were shown.
    static final String ON_PROPERTIES = "onProperties";
    /// The name of the spy recording the documents deleted (Stroom's `DeleteDocumentEvent`).
    static final String ON_DELETE = "onDelete";

    private static final String PATH = "/content/v1/fetchDependencies";

    // {fromType, fromUuid, fromName, toType, toUuid, toName, ok}
    private static final String[][] ROWS = {
            {"Pipeline", "p-1", "Alpha Pipeline", "XSLT", "x-1", "Alpha XSLT", "true"},
            {"Pipeline", "p-1", "Alpha Pipeline", "Feed", "f-1", "Missing Feed", "false"}};

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(PATH, request -> fetch(request, rows()))
            .build();
    private static final RestFixtures MANY_FIXTURES = RestFixtures.builder()
            .post(PATH, request -> fetch(request, manyRows()))
            .build();

    private DependenciesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DependenciesScreen", DependenciesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The action menu: Locate / Properties fire the app's events; Show dependants filters
                .story("ActionMenu", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alpha XSLT");
                    // The action buttons: [row1 From, row1 To, row2 From, row2 To]. Open row1's To
                    play.click(play.getAllByTitle(StroomDom.ACTIONS_TITLE).nth(1));
                    // All five GWT items are present
                    screen.findByText("Properties");
                    screen.findByText("Locate in Explorer");
                    screen.findByText("Show dependants");
                    play.expect(screen.getByText("Delete")).toBeInTheDocument();
                    play.expect(screen.getByText("Show dependencies")).toBeInTheDocument();
                    // Properties shows the document's dependency information
                    play.click(screen.getByText("Properties"));
                    play.waitFor(() -> play.expect(play.spy(ON_PROPERTIES)).toHaveBeenCalledWith("x-1"));
                    // Reopen: Locate fires LocateDocEvent
                    play.click(play.getAllByTitle(StroomDom.ACTIONS_TITLE).nth(1));
                    play.click(screen.findByText("Locate in Explorer"));
                    play.waitFor(() -> play.expect(play.spy(ON_LOCATE)).toHaveBeenCalledWith("x-1"));
                    // Reopen and show the dependants of Alpha XSLT: GWT writes `touuid:<uuid>` into
                    // the quick filter and lets the server apply it
                    play.click(play.getAllByTitle(StroomDom.ACTIONS_TITLE).nth(1));
                    play.click(screen.findByText("Show dependants"));
                    // GWT's quick filter has no label, only a placeholder
                    final Query filter = play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER);
                    play.waitFor(() -> play.expect(filter).toHaveValue("touuid:x-1"));
                    play.waitFor(3000, () -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(PATH).withJsonBodyContaining("{\"partialName\": \"touuid:x-1\"}")
                                    .toSpyMatcher()));
                    play.waitFor(3000, () -> play.expect(play.queryByText("Missing Feed")).toBeNull());
                    // Clearing the filter restores the full list
                    play.clear(filter);
                    play.waitFor(3000, () -> play.expect(play.getByText("Missing Feed")).toBeInTheDocument());
                    play.expect(play.spy(ON_DELETE)).not().toHaveBeenCalled();
                    ContentStorySupport.expectNoProblems(play);
                })
                // The grid loads; a resolved dependency shows OK, a broken one Missing; the quick
                // filter re-queries with partialName
                .story("Dependencies", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    play.findByText("Alpha XSLT");
                    play.expect(play.getByText("Missing Feed")).toBeInTheDocument();
                    play.expect(play.getByText("OK")).toBeInTheDocument();
                    play.expect(play.getByText("Missing")).toBeInTheDocument();
                    // GWT's quick filter has no label, only a placeholder
                    play.type(play.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "Missing");
                    play.waitFor(3000, () -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(PATH).withJsonBodyContaining("{\"partialName\": \"Missing\"}")
                                    .toSpyMatcher()));
                    play.waitFor(3000, () -> play.expect(play.queryByText("Alpha XSLT")).toBeNull());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Sorting and paging are the server's job: both are checked through the criteria
                // the screen sends
                .story("ServerSortAndPaging", context -> render(context, MANY_FIXTURES))
                .withPlay(play -> {
                    // Page 1 of 250: 100 rows, so row 000 is present and row 100 is not
                    play.findByText("Pipeline 000");
                    play.expect(play.queryByText("Pipeline 100")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(RequestMatcher.post(PATH)
                            .withJsonBodyContaining("{\"pageRequest\": {\"offset\": 0, \"length\": 100}}")
                            .toSpyMatcher());
                    // Next page: a new fetch at offset 100
                    play.click(play.getByTitle("Forward"));
                    play.findByText("Pipeline 100");
                    play.expect(play.queryByText("Pipeline 000")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(RequestMatcher.post(PATH)
                            .withJsonBodyContaining("{\"pageRequest\": {\"offset\": 100}}")
                            .toSpyMatcher());
                    // Sorting re-queries with the criteria field id
                    play.click(play.getByText(DependencyCriteria.FIELD_FROM_NAME));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(PATH)
                                    .withJsonBodyContaining("{\"sortList\": [{\"id\": \"From (Name)\"}]}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static List<String[]> rows() {
        final List<String[]> rows = new ArrayList<>();
        for (final String[] row : ROWS) {
            rows.add(row);
        }
        return rows;
    }

    private static List<String[]> manyRows() {
        final List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            final String number = pad(i);
            rows.add(new String[]{"Pipeline", "p-" + i, "Pipeline " + number, "XSLT", "x-" + i, "XSLT " + number,
                    "true"});
        }
        return rows;
    }

    private static String pad(final int number) {
        final String text = String.valueOf(number);
        return "000".substring(text.length()) + text;
    }

    // Stands in for the server: filters by the criteria's partialName, sorts by its sort list and
    // returns the requested page
    private static RestReply fetch(final RecordedRequest request, final List<String[]> allRows) {
        final Map<?, ?> criteria = (Map<?, ?>) JsonValues.parse(request.getBody());
        final String partialName = (String) criteria.get("partialName");
        final List<String[]> rows = new ArrayList<>();
        for (final String[] row : allRows) {
            if (matches(row, partialName)) {
                rows.add(row);
            }
        }
        final Object sortList = criteria.get("sortList");
        if (sortList instanceof List && !((List<?>) sortList).isEmpty()) {
            final Map<?, ?> sort = (Map<?, ?>) ((List<?>) sortList).get(0);
            final int index = sortIndex((String) sort.get("id"));
            Comparator<String[]> comparator = Comparator.comparing(row -> row[index]);
            if (Boolean.TRUE.equals(sort.get("desc"))) {
                comparator = comparator.reversed();
            }
            rows.sort(comparator);
        }
        int offset = 0;
        int length = 100;
        final Object pageRequest = criteria.get("pageRequest");
        if (pageRequest instanceof Map) {
            offset = number(((Map<?, ?>) pageRequest).get("offset"), 0);
            length = number(((Map<?, ?>) pageRequest).get("length"), 100);
        }
        final List<String> values = new ArrayList<>();
        for (int i = offset; i < offset + length && i < rows.size(); i++) {
            values.add(toJson(rows.get(i)));
        }
        return RestReply.json("{\"values\": [" + String.join(", ", values) + "], \"pageResponse\": {\"offset\": "
                + offset + ", \"length\": " + values.size() + ", \"total\": " + rows.size() + ", \"exact\": true}}");
    }

    private static boolean matches(final String[] row, final String partialName) {
        if (partialName == null || partialName.trim().isEmpty()) {
            return true;
        }
        final String filter = partialName.trim().toLowerCase();
        if (filter.startsWith("fromuuid:")) {
            return row[1].equals(filter.substring("fromuuid:".length()));
        } else if (filter.startsWith("touuid:")) {
            return row[4].equals(filter.substring("touuid:".length()));
        }
        return row[2].toLowerCase().contains(filter) || row[5].toLowerCase().contains(filter);
    }

    private static int sortIndex(final String id) {
        if (DependencyCriteria.FIELD_FROM_TYPE.equals(id)) {
            return 0;
        } else if (DependencyCriteria.FIELD_TO_TYPE.equals(id)) {
            return 3;
        } else if (DependencyCriteria.FIELD_TO_NAME.equals(id)) {
            return 5;
        } else if (DependencyCriteria.FIELD_STATUS.equals(id)) {
            return 6;
        }
        return 2;
    }

    private static int number(final Object value, final int defaultValue) {
        return value instanceof BigDecimal
                ? ((BigDecimal) value).intValue()
                : defaultValue;
    }

    private static String toJson(final String[] row) {
        return "{\"from\": {\"type\": \"" + row[0] + "\", \"uuid\": \"" + row[1] + "\", \"name\": \"" + row[2]
                + "\"}, \"to\": {\"type\": \"" + row[3] + "\", \"uuid\": \"" + row[4] + "\", \"name\": \"" + row[5]
                + "\"}, \"ok\": " + row[6] + "}";
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        harness.fn(ON_LOCATE);
        harness.fn(ON_PROPERTIES);
        harness.fn(ON_DELETE);
        harness.addRegistration(harness.getEventBus().addHandler(LocateDocEvent.getType(), event ->
                harness.spy(ON_LOCATE, event.getDocRef().getUuid())));
        harness.addRegistration(harness.getEventBus().addHandler(ShowDependenciesInfoDialogEvent.getType(),
                event -> harness.spy(ON_PROPERTIES, event.getDocRef().getUuid())));
        harness.addRegistration(harness.getEventBus().addHandler(DeleteDocumentEvent.getType(), event -> {
            for (final DocRef docRef : event.getDocRefs()) {
                harness.spy(ON_DELETE, docRef.getUuid());
            }
        }));
        harness.afterStartUp(() -> {
            final DependenciesTabPresenter presenter = harness.addContent(injector.getDependenciesTabPresenter());
            // As DependenciesPlugin does
            harness.addRegistration(harness.getEventBus().addHandler(ShowDocRefDependenciesEvent.getType(),
                    event -> {
                        final String field = DependencyType.DEPENDANT.equals(event.getDependencyType())
                                ? DependencyCriteria.FIELD_DEF_TO_UUID.getFilterQualifier()
                                : DependencyCriteria.FIELD_DEF_FROM_UUID.getFilterQualifier();
                        presenter.setQuickFilterText(field + ":" + event.getDocRef().getUuid());
                    }));
        });
        return harness.asWidget();
    }
}
