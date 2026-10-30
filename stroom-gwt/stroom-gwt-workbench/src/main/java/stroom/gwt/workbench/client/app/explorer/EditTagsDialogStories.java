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


package stroom.gwt.workbench.client.app.explorer;

import stroom.explorer.client.component.ExplorerNodeEditTagsController;
import stroom.explorer.client.event.ShowEditNodeTagsDialogEvent;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
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

/// Stories of `App/Main/EditTagsDialog`, showing Stroom's real [ExplorerNodeEditTagsController]
/// ('Edit Tags'/'Add Tags') and [ExplorerNodeRemoveTagsController] ('Remove Tags') with fake REST
/// replies.
///
/// The stories answer Stroom's `ExplorerResource`: all tags
/// (`GET /explorer/v2/fetchExplorerNodeTags`), a node's tags
/// (`POST /explorer/v2/fetchExplorerNodeTagsByDocRefs`), and setting (`PUT /explorer/v2/tags`),
/// adding (`PUT /explorer/v2/addTags`) and removing (`DELETE /explorer/v2/removeTags`) tags,
/// checked on the request spy. The dialogs are opened as the explorer's menu does, by firing their
/// events, with the controllers (from GIN) registered as the events' handlers in place of their
/// listeners. A chained request: the edit dialog fetches all tags, then the node's own tags, before
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
                    // The known tags are a list box of several choices, whose chosen tags are added
                    // with the arrow button
                    play.click(screen.getByRole("option", "x"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "x"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.click(screen.getByTitle("Add Selected Tags from All Known Tags"));
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
                    // The tags are options of a list box of several choices, not
                    // labelled check boxes, so 'drop' is selected rather than ticked
                    play.click(screen.getByRole("option", "drop"));
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(REMOVE_TAGS_PATH)
                                    .withJsonBodyContaining("{\"tags\": [\"drop\"]}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // The tag lists are list boxes of several choices, named by their labels. From the
                // field, Down goes to the known tags; Space chooses, moving only moves, and Enter
                // adds those chosen, going back to the field
                .story("KeysChooseAndAddTags",
                        context -> renderEdit(context, SINGLE, "[\"alpha\"]", "[\"alpha\", \"beta\", \"gamma\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(knownTags(screen).textContents())
                            .toEqual(Arrays.asList("beta", "gamma")));
                    final Query known = screen.getByRole("listbox", "All known tags");
                    play.expect(known).toHaveAttribute("aria-multiselectable", "true");
                    play.expect(screen.getByRole("listbox", "Document Tags"))
                            .toHaveAttribute("aria-multiselectable", "true");
                    final Query field = screen.getByTitle("Enter tags manually or filter 'All Known Tags'");
                    play.waitFor(() -> play.expect(field).toHaveFocus());

                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    // Up from the first known tag goes back to the field, and Down returns
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(field).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta"))
                            .toHaveAttribute("aria-selected", "true"));
                    // Each choice redraws the rows, so each key waits for the focus to come back
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    // Moving doesn't choose
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "gamma")).toHaveFocus());
                    play.expect(screen.getByRole("option", "gamma")).toHaveAttribute("aria-selected", "false");
                    play.expect(screen.getByRole("option", "beta")).toHaveAttribute("aria-selected", "true");
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "gamma"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "gamma")).toHaveFocus());

                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(nodeTags(screen).textContents())
                            .toEqual(Arrays.asList("alpha", "beta", "gamma")));
                    play.expect(knownTags(screen)).toHaveLength(0);
                    play.waitFor(() -> play.expect(field).toHaveFocus());
                    expectNoProblems(play);
                })
                // Delete removes the document tags chosen, which go back to the known tags; Space
                // takes a tag back out of those chosen
                .story("DeleteRemovesTags",
                        context -> renderEdit(context, SINGLE, "[\"alpha\", \"beta\"]", "[\"alpha\", \"beta\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(nodeTags(screen).textContents())
                            .toEqual(Arrays.asList("alpha", "beta")));
                    // A toolbar button stays focusable when disabled, so is only aria-disabled
                    final Query remove = screen.getByTitle("Remove Selected Tags");
                    play.expect(remove).toHaveAttribute("aria-disabled", "true");

                    play.click(screen.getByRole("option", "alpha"));
                    play.waitFor(() -> play.expect(remove).not().toHaveAttribute("aria-disabled"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "alpha")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    // Space again takes it back out
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta"))
                            .toHaveAttribute("aria-selected", "false"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());

                    play.keyboard("{Delete}");
                    play.waitFor(() -> play.expect(nodeTags(screen).textContents())
                            .toEqual(Arrays.asList("beta")));
                    play.expect(knownTags(screen).textContents()).toEqual(Arrays.asList("alpha"));
                    // The list keeps the focus, with nothing chosen
                    play.waitFor(() -> play.expect(screen.getByRole("option", "beta")).toHaveFocus());
                    play.expect(remove).toHaveAttribute("aria-disabled", "true");

                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE_TAGS_PATH)
                                    .withBody("tags are beta alone", body -> body != null
                                            && body.contains("\"beta\"") && !body.contains("\"alpha\""))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Filtering the known tags drops the chosen ones it hides, so that only tags that can
                // be seen are added
                .story("FilteringDropsHiddenChoices",
                        context -> renderEdit(context, SINGLE, "[]", "[\"beta\", \"gamma\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(knownTags(screen).textContents())
                            .toEqual(Arrays.asList("beta", "gamma")));
                    final Query add = screen.getByTitle("Add Selected Tags from All Known Tags");
                    play.click(screen.getByRole("option", "beta"));
                    play.waitFor(() -> play.expect(add).not().toHaveAttribute("aria-disabled"));

                    play.type(screen.getByTitle("Enter tags manually or filter 'All Known Tags'"), "gam");
                    play.waitFor(() -> play.expect(knownTags(screen).textContents())
                            .toEqual(Arrays.asList("gamma")));
                    play.expect(screen.getByText("All known tags (filtered)")).toBeInTheDocument();
                    play.expect(add).toHaveAttribute("aria-disabled", "true");
                    expectNoProblems(play);
                })
                // Over a hundred known tags: the list pages them, and its options scroll within its
                // border
                .story("ManyKnownTags", context -> renderEdit(context, SINGLE, "[]", manyTags(150)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByRole("option", "tag-000")).toBeInTheDocument());
                    play.expect(knownTags(screen)).toHaveLength(100);
                    play.expect(screen.getByRole("listbox", "All known tags")).toBeVisible();
                    expectNoProblems(play);
                })
                // Shift+Down moves to the next tag and chooses it (Shift+Up the one before), and
                // Shift+Space chooses the run from the last tag chosen to the one with the focus,
                // keeping the others
                .story("ShiftKeysChooseARun",
                        context -> renderRemove(context, MULTIPLE, "[\"a1\", \"a2\", \"a3\", \"a4\", \"a5\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a1")).toHaveFocus());
                    play.keyboard("{Shift>}{ArrowDown}{/Shift}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a2"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a2")).toHaveFocus());
                    play.expect(screen.getByRole("option", "a1")).toHaveAttribute("aria-selected", "false");
                    // Down twice, moving only, then Shift+Space: a2 to a4
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a3")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a4")).toHaveFocus());
                    play.keyboard("{Shift>} {/Shift}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a3"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.expect(screen.getByRole("option", "a4")).toHaveAttribute("aria-selected", "true");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a4")).toHaveFocus());
                    // Shift+Up back onto a3 takes it out again
                    play.keyboard("{Shift>}{ArrowUp}{/Shift}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "a3"))
                            .toHaveAttribute("aria-selected", "false"));
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(REMOVE_TAGS_PATH)
                                    .withBody("tags are a2 and a4", body -> body != null
                                            && body.contains("\"a2\"") && body.contains("\"a4\"")
                                            && !body.contains("\"a1\"") && !body.contains("\"a3\"")
                                            && !body.contains("\"a5\""))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Remove's list has the focus when the dialog opens; Space chooses each tag to remove
                .story("RemoveByKeyboard",
                        context -> renderRemove(context, MULTIPLE, "[\"keep\", \"drop\", \"old\"]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query list = screen.getByRole("listbox", "Existing Tags");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "drop")).toHaveFocus());
                    play.expect(list).toHaveAttribute("aria-multiselectable", "true");
                    // Each choice redraws the rows, so each key waits for the focus to come back
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "drop"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.waitFor(() -> play.expect(screen.getByRole("option", "drop")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "keep")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "old")).toHaveFocus());
                    play.keyboard(" ");
                    play.waitFor(() -> play.expect(screen.getByRole("option", "old"))
                            .toHaveAttribute("aria-selected", "true"));
                    play.expect(screen.getByRole("option", "keep")).toHaveAttribute("aria-selected", "false");
                    play.click(okButton(screen));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(REMOVE_TAGS_PATH)
                                    .withBody("tags are drop and old", body -> body != null
                                            && body.contains("\"drop\"") && body.contains("\"old\"")
                                            && !body.contains("\"keep\""))
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                });
    }

    private static Query okButton(final Play screen) {
        return screen.getByRole("button", StroomDom.button("OK"));
    }

    // The options of the document tags list box (the last list box in the dialog)
    private static Query nodeTags(final Play screen) {
        return screen.querySelectorAll(".explorer-node-edit-tags__node-tags [role=\"option\"]");
    }

    // The options of the known tags list box
    private static Query knownTags(final Play screen) {
        return screen.querySelectorAll(".explorer-node-edit-tags__all-tags [role=\"option\"]");
    }

    // A JSON array of that many tags, 'tag-000' onwards
    private static String manyTags(final int count) {
        final StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(", ");
            }
            // Three digits, so that the tags sort in number order
            final String number = String.valueOf(i);
            json.append("\"tag-").append("000".substring(number.length())).append(number).append("\"");
        }
        return json.append("]").toString();
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
        // As ExplorerPlugin does
        harness.addRegistration(harness.getEventBus().addHandler(ShowEditNodeTagsDialogEvent.getType(), event ->
                injector.getExplorerNodeEditTagsController().show(harness.getAppRoot(), event.getExplorerNodes())));
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
        harness.addRegistration(harness.getEventBus().addHandler(ShowRemoveNodeTagsDialogEvent.getType(), event ->
                injector.getExplorerNodeRemoveTagsController().show(harness.getAppRoot(), event.getExplorerNodes())));
        // As the explorer's 'Remove Tags' menu item does
        ShowRemoveNodeTagsDialogEvent.fire(harness.getHasHandlers(), nodes);
        return harness.asWidget();
    }
}
