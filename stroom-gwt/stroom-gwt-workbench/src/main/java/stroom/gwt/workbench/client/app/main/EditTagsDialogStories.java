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

import stroom.explorer.client.event.ShowEditNodeTagsDialogEvent;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.shared.ExplorerNode;
import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;

/// Stories of `App/Main/EditTagsDialog`, showing Stroom's real [ExplorerNodeEditTagsPresenter]
/// ('Edit Tags'/'Add Tags') and [ExplorerNodeRemoveTagsPresenter] ('Remove Tags') with fake REST
/// replies.
///
/// The stories answer Stroom's `ExplorerResource`: all tags
/// (`GET /explorer/v2/fetchExplorerNodeTags`), a node's tags
/// (`POST /explorer/v2/fetchExplorerNodeTagsByDocRefs`), and setting (`PUT /explorer/v2/tags`),
/// adding (`PUT /explorer/v2/addTags`) and removing (`DELETE /explorer/v2/removeTags`) tags,
/// checked on the request spy. The dialogs are opened as the explorer's menu does, by firing their
/// events, with the presenters (from GIN) registered as the events' handlers in place of their GWTP
/// proxies. A chained request: the edit dialog fetches all tags, then the node's own tags, before
/// it shows.
public final class EditTagsDialogStories {

    private static final String ALL_TAGS_PATH = "/explorer/v2/fetchExplorerNodeTags";
    private static final String NODE_TAGS_PATH = "/explorer/v2/fetchExplorerNodeTagsByDocRefs";
    private static final String UPDATE_TAGS_PATH = "/explorer/v2/tags";
    private static final String ADD_TAGS_PATH = "/explorer/v2/addTags";
    private static final String REMOVE_TAGS_PATH = "/explorer/v2/removeTags";

    // The explorer's node for 'MyDict' holds its tags, as the server's tree does, so that an
    // untouched set is seen as unchanged
    private static final List<ExplorerNode> SINGLE = List.of(node("MyDict", "u1", "alpha"));
    private static final List<ExplorerNode> MULTIPLE = List.of(node("A", "u1"), node("B", "u2"));

    private EditTagsDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/EditTagsDialog", EditTagsDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Single node: edit its tags; the current set is shown; adding a tag + OK replaces the set
                .story("EditSingle", context -> renderEdit(context, SINGLE, "[\"alpha\"]", "[\"alpha\", \"beta\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Edit Tags on MyDict")).toBeInTheDocument());
                    // Its current tag is shown
                    play.waitFor(() -> play.expect(nodeTags(screen).textContents())
                            .toEqual(Arrays.asList("alpha")));
                    // Add a new tag via the input.
                    // The input has no placeholder; it is found by its title
                    play.type(screen.getByTitle("Enter tags manually or filter 'All Known Tags'"), "gamma{enter}");
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE_TAGS_PATH)
                                    .withBody("tags are alpha and gamma", body -> body != null
                                            && body.contains("\"alpha\"") && body.contains("\"gamma\""))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // GWT's onHideRequest no-ops when nothing changed: no REST call, no tree refresh
                .story("OkWithNoChangeSkipsTheServer",
                        context -> renderEdit(context, SINGLE, "[\"alpha\"]", "[\"alpha\", \"beta\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Edit Tags on MyDict")).toBeInTheDocument());
                    // Wait for the load to settle so OK is pressed against a seeded set
                    play.waitFor(() -> play.expect(nodeTags(screen).textContents())
                            .toEqual(Arrays.asList("alpha")));
                    play.click(okButton(screen));
                    // The dialog closes without updating the tags. Checked after a beat, so that a
                    // late call would still be caught
                    play.sleep(200);
                    play.expect(screen.queryByText("Edit Tags on MyDict")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE_TAGS_PATH).toSpyMatcher());
                    expectNoProblems(play);
                })
                // Multiple nodes: 'Add Tags', picks tags to add to all via addTags
                .story("AddMulti", context -> renderEdit(context, MULTIPLE, "[]", "[\"x\", \"y\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Add Tags to 2 Documents")).toBeInTheDocument());
                    // Pick a known tag.
                    // The known tags are a list box, whose selected tags are added with the arrow
                    // button
                    play.selectOptions(screen.getByTitle("All tags currently in use across all documents"), "x");
                    play.click(screen.getByTitle("Add selected tags from 'All Known Tags'"));
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(ADD_TAGS_PATH)
                                    .withJsonBodyContaining("{\"tags\": [\"x\"], \"docRefs\": [{\"uuid\": \"u1\"}, "
                                            + "{\"uuid\": \"u2\"}]}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Remove: shows the tags held by the selection; selecting one + OK removes it
                .story("Remove", context -> renderRemove(context, MULTIPLE, "[\"keep\", \"drop\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Remove Tags from 2 Documents"))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(screen.getByText("drop")).toBeInTheDocument());
                    // The tags are options of a multi-select list box, not
                    // labelled check boxes, so 'drop' is selected rather than ticked
                    play.selectOptions(screen.getByRole("listbox"), "drop");
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(REMOVE_TAGS_PATH)
                                    .withJsonBodyContaining("{\"tags\": [\"drop\"]}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    private static Query okButton(final Play screen) {
        return screen.getByRole("button", StroomDom.button("OK"));
    }

    // The options of the document tags list box (the last list box in the dialog)
    private static Query nodeTags(final Play screen) {
        return screen.querySelectorAll(".editNodeTagsNodeTagsFrmGrp option");
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static ExplorerNode node(final String name, final String uuid, final String... tags) {
        final ExplorerNode.Builder builder = ExplorerNode.builder()
                .type("Dictionary")
                .uuid(uuid)
                .name(name);
        if (tags.length > 0) {
            builder.addTags(tags);
        }
        return builder.build();
    }

    private static RestFixtures fixtures(final String nodeTags, final String allTags) {
        return RestFixtures.builder()
                .get(ALL_TAGS_PATH, RestReply.json(allTags))
                .post(NODE_TAGS_PATH, RestReply.json(nodeTags))
                // updateNodeTags returns the updated node, i.e. the one sent
                .put(UPDATE_TAGS_PATH, request -> RestReply.json(request.getBody()))
                .put(ADD_TAGS_PATH, RestReply.noContent())
                .delete(REMOVE_TAGS_PATH, RestReply.noContent())
                .build();
    }

    private static Widget renderEdit(final StoryContext context,
                                     final List<ExplorerNode> nodes,
                                     final String nodeTags,
                                     final String allTags) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures(nodeTags, allTags))
                .injector(injector)
                .build();
        // As the presenter's GWTP proxy would
        final ExplorerNodeEditTagsPresenter presenter = injector.getExplorerNodeEditTagsPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ShowEditNodeTagsDialogEvent.getType(), presenter));
        // As the explorer's 'Edit Tags'/'Add Tags' menu item does
        ShowEditNodeTagsDialogEvent.fire(harness.getHasHandlers(), nodes);
        return harness.asWidget();
    }

    private static Widget renderRemove(final StoryContext context,
                                       final List<ExplorerNode> nodes,
                                       final String nodeTags) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures(nodeTags, "[]"))
                .injector(injector)
                .build();
        final ExplorerNodeRemoveTagsPresenter presenter = injector.getExplorerNodeRemoveTagsPresenter();
        harness.addRegistration(harness.getEventBus()
                .addHandler(ShowRemoveNodeTagsDialogEvent.getType(), presenter));
        // As the explorer's 'Remove Tags' menu item does
        ShowRemoveNodeTagsDialogEvent.fire(harness.getHasHandlers(), nodes);
        return harness.asWidget();
    }
}
