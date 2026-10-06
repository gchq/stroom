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
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.explorer.client.event.ExplorerTaskMonitorFactory;
import stroom.explorer.shared.ExplorerResource;
import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;

/// Stories matching `App/Main/DocInfoDialog` in the React Storybook, showing Stroom's real
/// [InfoDocumentPresenter] (a document's 'Info' dialog) with fake REST replies.
///
/// The React story passes the `ExplorerNodeInfo` to the dialog as a prop. In Stroom the explorer's
/// Info menu item (`DocumentPluginEventManager`) fetches it with `POST /explorer/v2/info` and fires
/// `ShowInfoDocumentDialogEvent`, which the presenter's GWTP proxy passes on to it; the story does
/// both, with the React fixture as the reply, and registers the presenter (from GIN) as the
/// event's handler in place of the proxy.
public final class DocInfoDialogStories {

    private static final DocRef DOC_REF = new DocRef("Dictionary", "dict-uuid-1", "Countries");

    private static final String NODE = """
            {"type": "Dictionary", "uuid": "dict-uuid-1", "name": "Countries", "tags": ["reference", "geo"]}""";
    private static final String UNTAGGED_NODE = """
            {"type": "Dictionary", "uuid": "dict-uuid-1", "name": "Countries"}""";

    // Upstream #5553 replaced ExplorerNodeInfo.docRefInfo with a page of audit entries
    private static final String AUDIT_ENTRIES = """
            {
              "values": [
                {"time": 1700000000000, "user": {"displayName": "alice"}, "action": "CREATE"},
                {"time": 1700100000000, "user": {"displayName": "bob"}, "action": "UPDATE"},
                {"time": 1700200000000, "user": {"displayName": "carol"}, "action": "RENAME"}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final String INFO_PATH = "/explorer/v2/info";

    private DocInfoDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DocInfoDialog", DocInfoDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Identity lines, sorted tags, then the audit history table
                .story("WithAuditHistory", context -> render(context,
                        "{\"explorerNode\": " + NODE + ", \"auditEntries\": " + AUDIT_ENTRIES + "}"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: GWT bolds the key and appends the ': ' after it, so the key's
                    // element holds 'UUID' (React's 'UUID:')
                    screen.findByText("UUID");
                    // GWT reads identity off the explorer node, not a separate doc-ref-info block
                    play.expect(screen.getByText("dict-uuid-1")).toBeInTheDocument();
                    play.expect(screen.getByText("Dictionary")).toBeInTheDocument();
                    play.expect(screen.getByText("Countries")).toBeInTheDocument();
                    // Tags are sorted alphabetically: 'geo' before 'reference' despite the fixture order
                    play.expect(screen.getByText("Tags")).toBeInTheDocument();
                    play.expect(screen.getAllByText(TextMatch.regex("^(geo|reference)$", "")).textContents())
                            .toEqual(Arrays.asList("geo", "reference"));
                    // The action column renders AuditAction.getDisplayValue(), the past-tense label
                    play.expect(screen.getByText("Audit Info")).toBeInTheDocument();
                    play.expect(screen.getByText("Created")).toBeInTheDocument();
                    play.expect(screen.getByText("Updated")).toBeInTheDocument();
                    play.expect(screen.getByText("Renamed")).toBeInTheDocument();
                    play.expect(screen.queryByText("CREATE")).toBeNull();
                    play.expect(screen.getByText("alice")).toBeInTheDocument();
                    play.expect(screen.getByText("carol")).toBeInTheDocument();
                    expectInfoFetched(play);
                })
                // No audit entries: the whole Audit Info block is omitted, as GWT omits its table.
                // Differs from React: the React fixture leaves auditEntries out; Stroom's
                // ExplorerNodeInfo requires it (the server always sends a page, maybe empty), so
                // this reply has an empty page
                .story("WithoutAuditHistory", context -> render(context, "{\"explorerNode\": " + NODE
                        + ", \"auditEntries\": {\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, "
                        + "\"total\": 0, \"exact\": true}}}"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("UUID");
                    play.expect(screen.getByText("Countries")).toBeInTheDocument();
                    play.expect(screen.queryByText("Audit Info")).toBeNull();
                    expectInfoFetched(play);
                })
                // An untagged document omits the Tags block entirely (GWT NullSafe.hasItems)
                .story("WithoutTags", context -> render(context,
                        "{\"explorerNode\": " + UNTAGGED_NODE + ", \"auditEntries\": " + AUDIT_ENTRIES + "}"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("UUID");
                    play.expect(screen.queryByText("Tags")).toBeNull();
                    play.expect(screen.getByText("Audit Info")).toBeInTheDocument();
                    expectInfoFetched(play);
                });
    }

    private static void expectInfoFetched(final Play play) {
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(RequestMatcher.post(INFO_PATH)
                .withJsonBodyContaining("{\"uuid\": \"dict-uuid-1\"}")
                .toSpyMatcher());
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final String explorerNodeInfo) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder()
                        .post(INFO_PATH, RestReply.json(explorerNodeInfo))
                        .build())
                .injector(injector)
                .build();

        // As the presenter's GWTP proxy would
        final InfoDocumentPresenter presenter = injector.getInfoDocumentPresenter();
        harness.addRegistration(harness.getEventBus()
                .addHandler(ShowInfoDocumentDialogEvent.getType(), presenter));

        // As the explorer's Info menu item (DocumentPluginEventManager) does
        final ExplorerResource explorerResource = GWT.create(ExplorerResource.class);
        harness.getRestFactory()
                .create(explorerResource)
                .method(res -> res.info(DOC_REF))
                .onSuccess(info -> ShowInfoDocumentDialogEvent.fire(harness.getHasHandlers(), info))
                .taskMonitorFactory(new ExplorerTaskMonitorFactory(harness.getHasHandlers()))
                .exec();
        return harness.asWidget();
    }
}
