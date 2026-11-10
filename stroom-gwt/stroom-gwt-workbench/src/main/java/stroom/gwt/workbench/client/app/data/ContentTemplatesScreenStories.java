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


package stroom.gwt.workbench.client.app.data;

import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.receive.content.client.component.ContentTemplateTabController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/ContentTemplatesScreen`, showing Stroom's real
/// [ContentTemplateTabController] (the 'Content Templates' tab, with its 'Edit Template' dialog)
/// with fake REST replies.
///
/// The stories answer Stroom's `ContentTemplateResource` (`/contentTemplates/v1/`): `GET` (the
/// templates), `PUT` (an echo) and `GET /fields`, checked on the request spy, and the pipeline
/// picker's explorer requests ([TreeFixtures], `decorate`).
public final class ContentTemplatesScreenStories {

    private static final String PATH = "/contentTemplates/v1";

    private static final String TEMPLATE = """
            {"templateNumber": NUMBER, "enabled": true, "name": "NAME",DESCRIPTION "templateType": "TYPE",
              "copyElementDependencies": false, "pipeline": {"type": "Pipeline", "uuid": "PIPE_UUID",
              "name": "PIPE_NAME"}, "processorPriority": 10, "processorMaxConcurrent": 1,
              "expression": {"type": "operator", "op": "AND", "enabled": true, "children": [
                {"type": "term", "field": "Feed", "condition": "CONTAINS", "value": "VALUE", "enabled": true}]}}""";

    private static final String DOC = "{\"type\": \"ContentTemplates\", \"uuid\": \"ct-1\", \"name\": \"Templates\", "
            + "\"contentTemplates\": ["
            + template("1", "My template", " \"description\": \"Creates a processor filter.\",", "PROCESSOR_FILTER",
            "p1", "My pipe", "X")
            + ", "
            // No description: GWT hides the Description group for this one
            + template("2", "Undescribed", "", "INHERIT_PIPELINE", "p2", "Other pipe", "Y")
            + "]}";

    private static final RestFixtures FIXTURES = TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree())
            .get(PATH, RestReply.json(DOC))
            .put(PATH, ContentTemplatesScreenStories::echo)
            .get("/contentTemplates/v1/fields", RestReply.json("[{\"fldName\": \"Feed\", \"fldType\": \"TEXT\"}]"))
            // The editor's pipeline picker checks its pipeline exists and gets its node
            .post(TreeFixtures.DECORATE, ContentStorySupport::decorate)
            .build();

    private static final String EDIT_TEMPLATE = "Edit Template";

    private ContentTemplatesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ContentTemplatesScreen", ContentTemplatesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // OK runs GWT's isTemplateValid checks and, on failure, alerts and keeps the dialog
                // open: name, then a name used by another template; its own name is fine
                .story("EditValidation", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("My template");
                    play.dblClick(play.getByText("My template"));
                    final Play dialog = screen.within(screen.findByText(EDIT_TEMPLATE).closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("Template Name");
                    // 1. A blank name
                    play.clear(name);
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    dismissAlert(play, "The template must have a name.");
                    // 2. A name already used by another template
                    play.clear(name);
                    play.type(name, "Undescribed");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    dismissAlert(play, "Name 'Undescribed' is already in use.");
                    // 3. Its own name is fine (GWT compares template numbers)
                    play.clear(name);
                    play.type(name, "My template");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(EDIT_TEMPLATE)).toBeNull());
                    // Nothing was saved: the document is only saved by "Save Templates"
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(PATH).toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "ERROR: The template must have a name.");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The Description and Expression groups below the grid follow the selection, each
                // hidden when it has nothing to show
                .story("DetailPanels", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    play.findByText("My template");
                    // GWT hides a FormGroup (display: none) rather than removing it, so its groups
                    // are found by their labels
                    final Query description = play.getByText("Description", "label");
                    final Query expression = play.getByText("Expression", "label");
                    // The first template is selected, as the panes show it: both groups show; the
                    // expression is read only
                    play.waitFor(() -> play.expect(description).toBeVisible());
                    play.expect(play.getByText("Creates a processor filter.")).toBeVisible();
                    play.expect(expression).toBeVisible();
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.regex("Feed\\s+contains\\s+X", "i")))
                            .toBeInTheDocument());
                    play.expect(play.queryByDisplayValue("X")).toBeNull();
                    // Select the one with no description: the Description group hides
                    play.click(play.getByText("Undescribed"));
                    play.waitFor(() -> play.expect(description).not().toBeVisible());
                    play.expect(expression).toBeVisible();
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.regex("Feed\\s+contains\\s+Y", "i")))
                            .toBeInTheDocument());
                    ContentStorySupport.expectNoProblems(play);
                })
                // The row's action menu acts on its row
                .story("RowActionMenu", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("My template");
                    // Row 1 of 2: the full set, Move Down but no Move Up
                    // Each row's button is named for its template, as its menu is
                    play.click(play.getByTitle("Actions for template 1 'My template'"));
                    play.waitFor(() -> play.expect(screen.getByText("Edit Template")).toBeVisible());
                    for (final String label : new String[]{"Add Template Above", "Add Template Below",
                            "Edit Template", "Duplicate Template", "Delete Template"}) {
                        play.expect(screen.getByText(label)).toBeVisible();
                    }
                    play.expect(screen.queryByText("Move Template Up")).toBeNull();
                    play.expect(screen.getByText("Move Template Down")).toBeVisible();
                    // Duplicate acts on that row and names the copy "<name> (copy)"
                    play.click(screen.getByText("Duplicate Template"));
                    play.waitFor(() -> play.expect(play.getByText("My template (copy)")).toBeInTheDocument());
                    // The last row has Move Up but no Move Down
                    play.click(play.within(play.getByText("Undescribed").closest(StroomDom.GRID_ROW))
                            .getByTitle(TextMatch.regex("^Actions for template ")));
                    play.waitFor(() -> play.expect(screen.getByText("Move Template Up")).toBeVisible());
                    play.expect(screen.queryByText("Move Template Down")).toBeNull();
                    // Closed, so the accessibility check doesn't measure the buttons it covers (a
                    // row's button is a 24px target, but one partly under the menu isn't)
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByText("Move Template Up")).toBeNull());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Editing a template (rename) dirties the document; Save Templates saves it
                .story("Templates", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("My template");
                    // GWT shows the raw TemplateType value
                    play.expect(play.getByText("PROCESSOR_FILTER")).toBeInTheDocument();
                    play.dblClick(play.getByText("My template"));
                    final Play dialog = screen.within(screen.findByText(EDIT_TEMPLATE).closest(StroomDom.DIALOG));
                    final Query name = dialog.getByLabelText("Template Name");
                    play.clear(name);
                    play.type(name, "Renamed template");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.findByText("Renamed template");
                    final Query save = play.getByTitle("Save Templates");
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(PATH)
                                    .withJsonBodyContaining("{\"contentTemplates\": [{\"name\": \"Renamed template\"}, "
                                            + "{\"name\": \"Undescribed\"}]}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // A template added below the selected one is selected, and its row is the one shown
                // selected. Before, the first row was selected instead
                .story("AddTemplateSelectsIt", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Undescribed"));
                    play.waitFor(() -> play.expect(row(play, "Undescribed")).toHaveClass(StroomDom.SELECTED_ROW));
                    play.click(play.getByTitle("Add Template Below"));
                    final Play dialog = screen.within(screen.findByText("Add New Template", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // A template needs a name, a type, a pipeline and a term
                    play.type(dialog.getByLabelText("Template Name"), "Third template");
                    play.click(dialog.getByLabelText("Template Type"));
                    play.click(screen.findByRole("option", "PROCESSOR_FILTER"));
                    play.waitFor(() -> play.expect(dialog.getByLabelText("Template Type"))
                            .toHaveValue("PROCESSOR_FILTER"));
                    play.fireEvent().mouseDown(dialog.querySelector(".drop-down"), EventInit.create());
                    final Play picker = screen.within(screen.findByText("Choose item", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(picker.within(picker.findByText("Pipelines").closest(".explorer-cell"))
                            .querySelector(".explorer-cell__expander"));
                    play.click(picker.findByText("Ingest"));
                    play.click(picker.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Choose item", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.click(dialog.getByTitle("Add Term"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Add New Template", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The new template, the third, is the one selected
                    play.waitFor(() -> play.expect(row(play, "Third template")).toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(play.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    play.expect(row(play, "My template")).not().toHaveClass(StroomDom.SELECTED_ROW);
                    ContentStorySupport.expectNoProblems(play);
                })
                // Deleting a template selects the template now in its place (UI-GUIDE.md, "What a
                // list selects"). It once looked the deleted template up after the templates were
                // renumbered, found nothing and threw
                .story("DeleteSelectsNext", ContentTemplatesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("My template"));
                    play.waitFor(() -> play.expect(row(play, "My template")).toHaveClass(StroomDom.SELECTED_ROW));
                    play.click(play.getByRole("button", TextMatch.containing("Delete Selected Template")));
                    screen.findByText(TextMatch.containing("delete template 1"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("My template")).toBeNull());
                    play.waitFor(() -> play.expect(row(play, "Undescribed")).toHaveClass(StroomDom.SELECTED_ROW));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // The grid row showing the text
    private static Query row(final Play play, final String text) {
        return play.getByText(text).closest(StroomDom.GRID_ROW);
    }

    // The alert is Stroom's alert dialog; the edit dialog stays open behind it (e.reset())
    private static void dismissAlert(final Play play, final String message) {
        final Play screen = play.screen();
        play.waitFor(() -> play.expect(screen.getByText(message)).toBeInTheDocument());
        play.expect(screen.getByText(EDIT_TEMPLATE)).toBeInTheDocument();
        play.click(screen.within(screen.getByText(message).closest(StroomDom.DIALOG))
                .getByRole("button", StroomDom.button("Close")));
        play.waitFor(() -> play.expect(screen.queryByText(message)).toBeNull());
    }

    private static String template(final String number,
                                   final String name,
                                   final String description,
                                   final String type,
                                   final String pipeUuid,
                                   final String pipeName,
                                   final String value) {
        return TEMPLATE.replace("NUMBER", number)
                .replace("NAME", name)
                .replace("DESCRIPTION", description)
                .replace("TYPE", type)
                .replace("PIPE_UUID", pipeUuid)
                .replace("PIPE_NAME", pipeName)
                .replace("VALUE", value);
    }

    private static RestReply echo(final RecordedRequest request) {
        return RestReply.json(request.getBody());
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                // The validation errors are shown in Stroom's real alert dialog
                .realAlerts()
                .build();
        harness.afterStartUp(() -> harness.show(injector.getContentTemplateTabController()));
        return harness.asWidget();
    }
}
