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


package stroom.gwt.workbench.client.widgets.query;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.main.ContentStorySupport;
import stroom.gwt.workbench.client.app.rest.RestHandler;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.dateandtime.DateTimeWidgets;
import stroom.gwt.workbench.client.widgets.selectors.SelectorWidgets;
import stroom.gwt.workbench.client.widgets.selectors.UserFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DataSourceClient;
import stroom.query.client.DynamicFieldSelectionListModel;
import stroom.query.client.FieldSelectionListModel;
import stroom.query.client.SimpleFieldSelectionListModel;
import stroom.query.client.component.ExpressionEditor;
import stroom.query.client.component.ExpressionEditorController;
import stroom.query.client.component.ExpressionTree;
import stroom.query.client.component.ExpressionTreeController;
import stroom.query.client.component.TermEditorController;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for Stroom's [ExpressionEditorController] (an [ExpressionTreeController] under a toolbar):
/// the editor of a query expression, a tree of operators and terms, each term edited (when
/// selected) with a field picker, a condition and a value editor for the field's type.
///
/// The term editors' document and user pickers are Stroom's (see [SelectorWidgets]), answered by
/// a fixed explorer tree and users. A field list served by a data source is Stroom's
/// `DynamicFieldSelectionListModel`, answered by a [FieldFixture].
public final class ExpressionBuilderStories {

    private static final String ON_CHANGE = "onChange";
    private static final String FIELD_SOURCE = "fieldSource";
    private static final String DUPLICATE = "Duplicate Selected Expression Item";
    private static final List<String> TOOLBAR = List.of("Add Term", "Add Operator", DUPLICATE,
            "Disable Selected Expression Item", "Delete Selected Expression Item");
    private static final String POPUP = ".selection-popup";
    // The field picker of the selected term (a Stroom SelectionBox), and its parts
    private static final String FIELD_PICKER = ".term-editor__item--field";
    private static final DocRef DATA_SOURCE = new DocRef("Index", "index-uuid-1", "Example Index");
    // The looks of an item while another is moved: one that would take it, one that won't, and the
    // one moved (with the items in it)
    private static final String TARGET = "expression-item--target";
    private static final String REFUSED = "expression-item--refused";
    private static final String DETACHED = "expression-item--detached";
    private static final String MARKED = "." + TARGET + ", ." + REFUSED + ", ." + DETACHED;
    // The tree's live region, which says what moving with the keyboard did
    private static final String STATUS = ".draggable-tree__status";
    private static final String USER_ID = "UserId contains admin";
    private static final String EVENT_TIME = "EventTime between 2024-01-01T00:00:00.000Z,2024-02-01T00:00:00.000Z";

    private ExpressionBuilderStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // Term value autocomplete is Stroom's suggestion service, which the stories answer with no
        // suggestions.
        registry.component("Widgets/Query/ExpressionBuilder", ExpressionBuilderStories.class)
                .layout(StoryLayout.CENTERED)
                // A populated expression. Select a node to edit it inline; add, copy, disable or
                // delete with the toolbar, or drag a node onto an operator to move it.
                .story("Populated", context -> editor(context, populatedExpression(), false))
                // A larger expression, with no play, to look at: AND, OR and NOT operators four deep,
                // a disabled operator and a disabled term, and each kind of value (text, number, a
                // date range, a list, a document and a user)
                .story("ComplexExpression", context -> editor(context, complexExpression(), false))
                // Moving round the tree with the keyboard: it is one tab stop, Left goes to an item's
                // operator and Right into its first item, Up and Down to its siblings and Home and
                // End to the first and last of them; the selection follows the focus, shown as the
                // item's text (not its editor), and a click edits; Delete deletes
                .story("KeyboardNavigation", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Query tree = play.getByRole("tree", "Expression");
                    play.expect(tree).toBeInTheDocument();
                    play.expect(play.querySelectorAll("[role='tree'] [tabindex='0']")).toHaveLength(1);
                    final Query root = item(play, "AND, 4 items");
                    play.expect(root).toHaveAttribute("tabindex", "0");
                    play.expect(root).toHaveAttribute("aria-level", "1");

                    // Into the tree from the toolbar
                    play.run("focus the toolbar's last button",
                            () -> play.getByTitle("Delete Selected Expression Item").element().get().focus());
                    play.keyboard("{Tab}");
                    play.expect(root).toHaveFocus();

                    // Right, Down, Right (quickly): into the OR's first term
                    play.keyboard("{ArrowRight}{ArrowDown}{ArrowRight}");
                    final Query eventTime = item(play,
                            "EventTime between 2024-01-01T00:00:00.000Z,2024-02-01T00:00:00.000Z");
                    play.waitFor(() -> play.expect(eventTime).toHaveFocus());
                    play.expect(eventTime).toHaveAttribute("aria-level", "3");
                    play.expect(eventTime).toHaveAttribute("aria-posinset", "1");
                    play.expect(eventTime).toHaveAttribute("aria-setsize", "3");
                    play.expect(eventTime).toHaveAttribute("aria-selected", "true");
                    play.expect(item(play, "OR, 3 items")).toHaveAttribute("aria-expanded", "true");
                    // Selected with the keyboard: shown as its text, not its editor
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();

                    // Up and Down stop at the ends; Home and End go to them
                    play.keyboard("{ArrowUp}");
                    play.expect(eventTime).toHaveFocus();
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect(item(play, "StreamId in 1,2,3")).toHaveFocus());
                    play.keyboard("{ArrowDown}");
                    play.expect(item(play, "StreamId in 1,2,3")).toHaveFocus();
                    play.keyboard("{Home}{ArrowDown}{ArrowRight}{ArrowDown}");
                    final Query streamId = item(play, "StreamId > 1000");
                    play.waitFor(() -> play.expect(streamId).toHaveFocus());
                    play.expect(streamId).toHaveAttribute("aria-level", "4");
                    // Left goes back to its operator
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(item(play, "AND, 3 items")).toHaveFocus());

                    // A disabled item says so
                    play.expect(item(play, "NOT, 1 item, disabled")).toBeInTheDocument();
                    play.expect(item(play, "RunAsUser is Alice Anderson, disabled")).toBeInTheDocument();

                    // A click edits the item
                    play.click(play.getByText("StreamId > 1000", ".expression-item__label"));
                    play.waitFor(() -> play.expect(play.querySelector(FIELD_PICKER)).not().toBeNull());
                    play.waitFor(() -> play.expect(item(play, "StreamId > 1000")).toHaveFocus());

                    // Delete deletes the focused item, and the focus goes to the item selected in its
                    // place
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(item(play, "Feed is TEST_FEED")).toHaveFocus());
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();
                    play.keyboard("{Delete}");
                    play.waitFor(() -> play.expect(play.queryByText("Feed is TEST_FEED")).toBeNull());
                    play.waitFor(() -> play.expect(item(play, "AND, 2 items")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());
                    play.expect(play.querySelectorAll("[role='tree'] [tabindex='0']")).toHaveLength(1);
                })
                // Editing with the keyboard: Enter or F2 edits the focused item and focuses its first
                // field, Tab and Shift+Tab move through its fields and past the last or first back to
                // the item, as Escape does; leaving keeps what was entered
                .story("KeyboardEditing", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Query fieldInput = play.querySelector(FIELD_PICKER + " .selection-box__input");
                    final Query value = play.querySelector(".term-editor__item [aria-label='Value']");
                    play.run("focus the toolbar's last button",
                            () -> play.getByTitle("Delete Selected Expression Item").element().get().focus());
                    play.keyboard("{Tab}{ArrowRight}");
                    final Query userId = item(play, "UserId contains admin");
                    play.waitFor(() -> play.expect(userId).toHaveFocus());

                    // Enter edits it, in its first field; Tab goes through the rest
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(fieldInput).toHaveFocus());
                    play.keyboard("{Tab}");
                    play.expect(play.querySelector(".term-editor__item--condition .selection-box__input"))
                            .toHaveFocus();
                    play.keyboard("{Tab}");
                    play.expect(value).toHaveFocus();
                    play.keyboard("{End}s");
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());

                    // Tab past the last field: back to the item, shown as its text, the change kept
                    play.keyboard("{Tab}");
                    final Query admins = item(play, "UserId contains admins");
                    play.waitFor(() -> play.expect(admins).toHaveFocus());
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();

                    // Shift+Tab before the first field, likewise
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(fieldInput).toHaveFocus());
                    play.keyboard("{Shift>}{Tab}{/Shift}");
                    play.waitFor(() -> play.expect(admins).toHaveFocus());
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();

                    // F2 edits too, and Escape goes back to the item from any field
                    play.keyboard("{F2}");
                    play.waitFor(() -> play.expect(fieldInput).toHaveFocus());
                    play.keyboard("{Tab}{Tab}");
                    play.expect(value).toHaveFocus();
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(admins).toHaveFocus());
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();
                    // The arrow keys carry on from it
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(item(play, "OR, 3 items")).toHaveFocus());

                    // An operator's one field
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(item(play, "AND, 4 items")).toHaveFocus());
                    play.keyboard("{Enter}");
                    final Query operator = play.querySelector(".term-editor__item .selection-box__input");
                    play.waitFor(() -> play.expect(operator).toHaveFocus());
                    play.keyboard("{Tab}");
                    play.waitFor(() -> play.expect(item(play, "AND, 4 items")).toHaveFocus());
                })
                // Moving an item (and the items in it) to another operator with the keyboard: Ctrl+X
                // detaches it, the arrow keys choose its new operator (each item saying whether it
                // would take it), Ctrl+V attaches it there, and Escape puts it back
                .story("KeyboardMove", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Query status = play.querySelector(STATUS);
                    play.run("focus the toolbar's last button",
                            () -> play.getByTitle("Delete Selected Expression Item").element().get().focus());
                    play.keyboard("{Tab}");
                    play.waitFor(() -> play.expect(item(play, "AND, 4 items")).toHaveFocus());

                    // The root can't be detached
                    play.keyboard("{Control>}x{/Control}");
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);

                    // Ctrl+X detaches UserId, which stays where it is, faded
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED));
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            TextMatch.startingWith(USER_ID + " detached.")));

                    // Moving on shows whether each item would take it: an operator would, a term won't
                    play.keyboard("{ArrowDown}");
                    final Query or = item(play, "OR, 3 items, can take " + USER_ID);
                    play.waitFor(() -> play.expect(or).toHaveFocus());
                    play.expect(or).toHaveClass(TARGET);
                    play.keyboard("{ArrowRight}");
                    final Query eventTime = item(play, EVENT_TIME + ", can't take " + USER_ID);
                    play.waitFor(() -> play.expect(eventTime).toHaveFocus());
                    play.expect(eventTime).toHaveClass(REFUSED);
                    play.expect(or).not().toHaveClass(TARGET);
                    // Not by colour alone: the "not allowed" icon
                    play.expect(play.querySelector("." + REFUSED + " .expression-item__refused-icon"))
                            .toBeVisible();
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            EVENT_TIME + " can't take " + USER_ID + "."));
                    play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED);

                    // Ctrl+V on the OR attaches it there, last, keeping the focus on it
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(or).toHaveFocus());
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            USER_ID + " attached to OR, 3 items."));
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveFocus());
                    play.expect(item(play, USER_ID)).toHaveAttribute("aria-level", "3");
                    play.expect(item(play, USER_ID)).toHaveAttribute("aria-posinset", "4");
                    play.expect(item(play, "OR, 4 items")).toBeInTheDocument();
                    play.expect(play.querySelectorAll(MARKED)).toHaveLength(0);
                    play.expect(play.querySelector(".expression-item__refused-icon")).toBeNull();
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());

                    // Ctrl+V on its own operator moves it to the end, as dropping it there does
                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect(item(play, EVENT_TIME)).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}{ArrowLeft}");
                    play.waitFor(() -> play.expect(item(play, "OR, 4 items, can take " + EVENT_TIME)).toHaveFocus());
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            EVENT_TIME + " moved to the end of OR, 4 items."));
                    play.waitFor(() -> play.expect(item(play, EVENT_TIME)).toHaveFocus());
                    play.expect(item(play, EVENT_TIME)).toHaveAttribute("aria-posinset", "4");

                    // An operator is detached with its items, and Escape puts it back
                    play.keyboard("{Home}");
                    // The inner AND (the root has 3 items now too)
                    final Query and = play.querySelector("[role='treeitem'][aria-level='3'][aria-label^='AND']");
                    play.waitFor(() -> play.expect(and).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            TextMatch.startingWith("AND, 3 items and the 5 items in it detached.")));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(6);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent("AND, 3 items put back."));
                    play.expect(play.querySelectorAll(MARKED)).toHaveLength(0);
                    play.expect(and).toHaveFocus();
                })
                // The keyboard's moves are in the context menu too, which Shift+F10 or the context
                // menu key opens: Detach, and while an item is detached "Attach '…' here" (only where
                // it would be taken) and "Put '…' back"
                .story("KeyboardContextMenu", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query status = play.querySelector(STATUS);
                    play.click(play.getByText(USER_ID, ".expression-item__label"));
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveFocus());

                    // Shift+F10 opens the menu; Detach detaches UserId
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Detach")));
                    play.waitFor(() -> play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED));

                    // A term won't take it, so it can't be attached there
                    play.click(play.getByText(EVENT_TIME, ".expression-item__label"));
                    final Query eventTime = item(play, EVENT_TIME + ", can't take " + USER_ID);
                    play.waitFor(() -> play.expect(eventTime).toHaveFocus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.expect(screen.findByRole("menuitem",
                            TextMatch.containing("Attach '" + USER_ID + "' here")))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(screen.getByRole("menuitem", TextMatch.containing("Put '" + USER_ID + "' back")))
                            .toBeInTheDocument();
                    play.expect(screen.queryByRole("menuitem", TextMatch.containing("Detach"))).toBeNull();
                    play.keyboard("{Escape}");
                    play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED);

                    // The OR takes it: the context menu key's menu attaches it there
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(item(play, "OR, 3 items, can take " + USER_ID)).toHaveFocus());
                    play.keyboard("{ContextMenu}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Attach '" + USER_ID + "' here")));
                    play.waitFor(() -> play.expect(status).toHaveTextContent(USER_ID + " attached to OR, 3 items."));
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveAttribute("aria-level", "3"));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);

                    // "Put '…' back" puts it back
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED));
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Put '" + USER_ID + "' back")));
                    play.waitFor(() -> play.expect(status).toHaveTextContent(USER_ID + " put back."));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);
                })
                // Dragging an item (with the items in it) to another operator: the item under the
                // pointer has a green border if it would take it, or a red one (and the "not allowed"
                // icon) if not, and nothing else is marked; dropping on a green one moves it there,
                // last, which reorders it when that is its own operator
                // A right-click on a term's value shows the field's edit items (Cut, Copy, Paste and
                // so on) above the expression's own items (gchq/stroom#5897); on its condition, a box
                // to choose from, only the expression's
                .story("TermFieldContextMenu", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query value = play.querySelector(".term-editor__item [aria-label='Value']");
                    play.click(play.getByText(USER_ID, ".expression-item__label"));
                    play.waitFor(() -> play.expect(value).toBeVisible());
                    play.rightClick(value);
                    final Query menu = screen.findByRole("menu");
                    play.expect(play.within(menu).getAllByRole("menuitem").first())
                            .toHaveTextContent(TextMatch.containing("Undo"));
                    play.expect(play.within(menu).getByRole("menuitem", TextMatch.containing("Select All")))
                            .toBeInTheDocument();
                    play.expect(play.within(menu).getByRole("menuitem", TextMatch.containing("Add Term")))
                            .toBeInTheDocument();
                    // Select All acts on the value, which keeps the focus
                    play.click(play.within(menu).getByRole("menuitem", TextMatch.containing("Select All")));
                    play.waitFor(() -> play.expect(value).toHaveFocus());

                    play.rightClick(play.querySelector(".term-editor__item--condition .selection-box__input"));
                    final Query conditionMenu = screen.findByRole("menu");
                    play.expect(play.within(conditionMenu).getByRole("menuitem", TextMatch.containing("Add Term")))
                            .toBeInTheDocument();
                    play.expect(play.within(conditionMenu).queryByRole("menuitem", TextMatch.containing("Undo")))
                            .toBeNull();
                })
                .story("DragToMove", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play tree = play.within(play.getByRole("tree", "Expression"));
                    liftItem(play, tree, USER_ID);

                    // A term can't have items: it refuses it, and is the only one marked
                    final Query eventTime = item(play, EVENT_TIME);
                    play.fireEvent().mouseMove(eventTime, EventInit.create().atCentreOf(eventTime).buttons(1));
                    play.waitFor(() -> play.expect(item(play, EVENT_TIME)).toHaveClass(REFUSED));
                    play.expect(play.querySelector("." + REFUSED + " .expression-item__refused-icon"))
                            .toBeVisible();
                    play.expect(play.querySelectorAll("." + TARGET)).toHaveLength(0);

                    // The OR takes it: it is the target, and the term no longer refuses
                    final Query or = operator(play, "OR");
                    play.fireEvent().mouseMove(or, EventInit.create().atCentreOf(or).buttons(1));
                    play.waitFor(() -> play.expect(operator(play, "OR")).toHaveClass(TARGET));
                    play.expect(item(play, EVENT_TIME)).not().toHaveClass(REFUSED);
                    play.expect(play.querySelector(".expression-item__refused-icon")).toBeNull();

                    // Over nothing, nothing is marked; back over the OR, it is again
                    final Query runAsUser = item(play, "RunAsUser is Alice Anderson, disabled");
                    play.fireEvent().mouseMove(runAsUser, EventInit.create().relativeTo(runAsUser, 250, 10).buttons(1));
                    play.waitFor(() -> play.expect(play.querySelectorAll(MARKED)).toHaveLength(0));
                    play.fireEvent().mouseMove(or, EventInit.create().atCentreOf(or).buttons(1));
                    play.waitFor(() -> play.expect(operator(play, "OR")).toHaveClass(TARGET));

                    // Dropping it there moves it into the OR, last
                    play.fireEvent().mouseUp(or, EventInit.create().atCentreOf(or));
                    play.waitFor(() -> play.expect(tree.queryByText(USER_ID)).toBeInTheDocument());
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveAttribute("aria-level", "3"));
                    play.expect(item(play, USER_ID)).toHaveAttribute("aria-posinset", "4");
                    play.expect(play.querySelectorAll(MARKED)).toHaveLength(0);
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());

                    // Dropped on its own operator, an item goes last
                    liftItem(play, tree, EVENT_TIME);
                    final Query orAgain = operator(play, "OR");
                    play.fireEvent().mouseMove(orAgain, EventInit.create().atCentreOf(orAgain).buttons(1));
                    play.waitFor(() -> play.expect(operator(play, "OR")).toHaveClass(TARGET));
                    play.fireEvent().mouseUp(orAgain, EventInit.create().atCentreOf(orAgain));
                    play.waitFor(() -> play.expect(tree.queryByText(EVENT_TIME)).toBeInTheDocument());
                    play.waitFor(() -> play.expect(item(play, EVENT_TIME)).toHaveAttribute("aria-posinset", "4"));
                })
                // A drag that isn't dropped on an operator puts what is dragged back where it was,
                // among its siblings as it was, changing nothing: released over nothing, over a term or
                // with Escape pressed. Dragging while an item is detached with the keyboard puts that
                // back first
                .story("DragToCancel", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play tree = play.within(play.getByRole("tree", "Expression"));
                    final Query status = play.querySelector(STATUS);

                    // Dragging puts back an item detached with the keyboard
                    play.click(play.getByText(USER_ID, ".expression-item__label"));
                    play.waitFor(() -> play.expect(item(play, USER_ID)).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(item(play, USER_ID + ", detached")).toHaveClass(DETACHED));
                    liftItem(play, tree, EVENT_TIME);
                    play.waitFor(() -> play.expect(status).toHaveTextContent(USER_ID + " put back."));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);

                    // Released over nothing
                    final Query runAsUser = item(play, "RunAsUser is Alice Anderson, disabled");
                    play.fireEvent().mouseMove(runAsUser, EventInit.create().relativeTo(runAsUser, 250, 10).buttons(1));
                    play.fireEvent().mouseUp(runAsUser, EventInit.create().relativeTo(runAsUser, 250, 10));
                    expectEventTimeBack(play, tree);

                    // Released over a term
                    liftItem(play, tree, EVENT_TIME);
                    final Query streamIds = item(play, "StreamId in 1,2,3");
                    play.fireEvent().mouseMove(streamIds, EventInit.create().atCentreOf(streamIds).buttons(1));
                    play.waitFor(() -> play.expect(item(play, "StreamId in 1,2,3")).toHaveClass(REFUSED));
                    play.fireEvent().mouseUp(streamIds, EventInit.create().atCentreOf(streamIds));
                    expectEventTimeBack(play, tree);

                    // Escape over an operator that would take it
                    liftItem(play, tree, EVENT_TIME);
                    final Query root = operator(play, "AND, 4");
                    play.fireEvent().mouseMove(root, EventInit.create().atCentreOf(root).buttons(1));
                    play.waitFor(() -> play.expect(operator(play, "AND, 4")).toHaveClass(TARGET));
                    play.keyboard("{Escape}");
                    expectEventTimeBack(play, tree);
                    // Releasing after Escape doesn't drop it
                    play.fireEvent().mouseUp(root, EventInit.create().atCentreOf(root));
                    expectEventTimeBack(play, tree);

                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                })
                // A tree too wide for its pane: moving to an item off screen with the keyboard
                // scrolls it into view, pressing on the background and dragging pans it, and dragging
                // an item to the pane's edge scrolls it, so that an operator off screen can take it
                .story("ScrollAndPan", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play tree = play.within(play.getByRole("tree", "Expression"));
                    // The background, which the pointer is over between the items (the tree's, not
                    // that of the copy of what is dragged)
                    final Query background = play.querySelector(
                            ".expression-tree-panel:not([aria-hidden='true']) > .expression-tree-panel__canvas");
                    final Query pane = play.querySelector(".dock-max");
                    play.run("narrow the tree's pane, so that it scrolls",
                            () -> pane.element().get().getStyle().setProperty("width", "200px"));
                    play.waitFor(() -> play.expect("the pane's scrollable width",
                            () -> scrollLeftMax(pane)).toBeGreaterThan(400));

                    // Moving to Description contains test, off screen, scrolls all of it into view
                    play.run("focus the toolbar's last button",
                            () -> play.getByTitle("Delete Selected Expression Item").element().get().focus());
                    play.keyboard("{Tab}{ArrowRight}{ArrowDown}{ArrowRight}{ArrowDown}{ArrowRight}");
                    play.waitFor(() -> play.expect(item(play, "Feed is TEST_FEED")).toHaveFocus());
                    play.keyboard("{End}{ArrowRight}");
                    final Query description = item(play, "Description contains test");
                    play.waitFor(() -> play.expect(description).toHaveFocus());
                    play.waitFor(() -> play.expect("its right beyond the pane's",
                            () -> description.right().get() - pane.right().get()).toBeLessThanOrEqual(0));

                    // Pressing on the background and dragging pans it, and selects nothing
                    play.run("scroll the pane", () -> pane.element().get().setScrollLeft(200));
                    // On the last row, right of RunAsUser (relative to the pane, which doesn't move)
                    play.fireEvent().mouseDown(background, EventInit.create().relativeTo(pane, 100, 212).buttons(1));
                    play.fireEvent().mouseMove(background, EventInit.create().relativeTo(pane, 175, 212).buttons(1));
                    play.fireEvent().mouseMove(background, EventInit.create().relativeTo(pane, 250, 212).buttons(1));
                    play.fireEvent().mouseUp(background, EventInit.create().relativeTo(pane, 250, 212));
                    play.expect("the pane's scroll", () -> scrollLeft(pane)).toBe(50.0);
                    play.expect(item(play, "Description contains test")).toHaveAttribute("aria-selected", "true");

                    // Dragging an item to the pane's left edge scrolls it back, as far as the tree goes,
                    // so that an operator off screen can take it
                    play.run("scroll the pane", () -> pane.element().get().setScrollLeft(300));
                    liftItem(play, tree, "Description contains test");
                    play.fireEvent().mouseMove(background, EventInit.create().relativeTo(pane, 5, 60).buttons(1));
                    play.waitFor(() -> play.expect("the pane's scroll", () -> scrollLeft(pane)).toBe(0.0));
                    // The disabled NOT, off screen before, takes it
                    final Query not = item(play, "NOT, 1 item, disabled");
                    play.fireEvent().mouseMove(not, EventInit.create().atCentreOf(not).buttons(1));
                    play.waitFor(() -> play.expect(item(play, "NOT, 1 item, disabled")).toHaveClass(TARGET));
                    play.fireEvent().mouseUp(not, EventInit.create().atCentreOf(not));
                    play.waitFor(() -> play.expect(tree.queryByText("Description contains test")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(operator(play, "Description contains test"))
                            .toHaveAttribute("aria-level", "3"));
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());
                })
                // Editing a term and leaving it keeps what it was: its condition, and its document
                // (which Stroom checks exists)
                .story("EditingKeepsATerm", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    for (final String name : List.of("StreamId in 1,2,3", "Feed is TEST_FEED", "StreamId > 1000")) {
                        play.click(play.getByText(name, ".expression-item__label"));
                        play.waitFor(() -> play.expect(play.querySelector(FIELD_PICKER)).not().toBeNull());
                        play.click(play.getByText(USER_ID, ".expression-item__label"));
                        play.waitFor(() -> play.expect(item(play, name)).toBeInTheDocument());
                    }
                })
                // A date term's From and To are date-time boxes that allow expressions: they keep
                // their text, Enter (which runs the query) doesn't open the Set Date And Time dialog,
                // Alt+Down does, and OK puts the time chosen in the term
                .story("DateTermBoxes", context -> editor(context, complexExpression(), false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.getByText(TextMatch.startingWith("EventTime between"), ".expression-item__label"));
                    play.waitFor(() -> play.expect(play.querySelector(FIELD_PICKER)).not().toBeNull());
                    final Query from = screen.getByRole("textbox", "From");
                    play.expect(from).toHaveValue("2024-01-01T00:00:00.000Z");
                    play.expect(screen.getByRole("textbox", "To")).toHaveValue("2024-02-01T00:00:00.000Z");
                    play.expect(from).toHaveAttribute("aria-haspopup", "dialog");
                    play.run("focus From", () -> from.element().get().focus());
                    play.keyboard("{Enter}");
                    play.sleep(100);
                    play.expect(screen.queryByText("Set Date And Time")).toBeNull();
                    play.keyboard("{Alt>}{ArrowDown}{/Alt}");
                    screen.findByText("Set Date And Time");
                    play.click(screen.getByRole("button", "Midday"));
                    play.click(screen.getByRole("button", "OK"));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Date And Time")).toBeNull());
                    play.expect(from).toHaveValue("2024-01-01T12:00:00.000Z");
                    // Escape leaves the term, keeping the time chosen
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(item(play,
                            "EventTime between 2024-01-01T12:00:00.000Z,2024-02-01T00:00:00.000Z"))
                            .toBeInTheDocument());
                })
                // A term's document is kept if checking it fails (e.g. the server can't be reached),
                // as it is if the document doesn't exist
                .story("DocumentKeptIfCheckingItFails", context -> editor(context, complexExpression(), false,
                        request -> RestReply.error(500, "Server error")))
                .withPlay(play -> {
                    play.click(play.getByText("Feed is TEST_FEED", ".expression-item__label"));
                    play.waitFor(() -> play.expect(play.querySelector(FIELD_PICKER)).not().toBeNull());
                    play.click(play.getByText(USER_ID, ".expression-item__label"));
                    play.waitFor(() -> play.expect(item(play, "Feed is TEST_FEED")).toBeInTheDocument());
                })
                // A term's document is kept if the term is left before checking it is done
                .story("DocumentKeptIfLeftBeforeItIsChecked", context -> editor(context, complexExpression(), false,
                        request -> ContentStorySupport.decorate(request).delayed(3000)))
                .withPlay(play -> {
                    play.click(play.getByText("Feed is TEST_FEED", ".expression-item__label"));
                    play.waitFor(() -> play.expect(play.querySelector(FIELD_PICKER)).not().toBeNull());
                    play.click(play.getByText(USER_ID, ".expression-item__label"));
                    play.waitFor(() -> play.expect(item(play, "Feed is TEST_FEED")).toBeInTheDocument());
                })
                // A new, empty expression (just the root AND)
                .story("NewExpression", context -> editor(context, emptyExpression(), false))
                // Read only - the populated expression, with no selection, editing or dragging
                .story("ReadOnly", ExpressionBuilderStories::readOnly)
                .withPlay(play -> {
                    // Stroom shows a read only expression (e.g. a processor filter's, or a query in
                    // the history) as the tree alone, with no toolbar, rather than a toolbar of
                    // disabled buttons
                    play.findAllByText(TextMatch.containing("UserId"), ".expression-item__label");
                    for (final String title : TOOLBAR) {
                        play.expect(play.queryByTitle(title)).toBeNull();
                    }
                    // Nothing can be selected
                    play.click(play.getByText(TextMatch.containing("UserId"), ".expression-item__label"));
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();
                })
                // The toolbar is made of real buttons (ToolbarButton)
                .story("ToolbarIsRealButtons", context -> editor(context, populatedExpression(), false))
                .withPlay(ExpressionBuilderStories::playToolbar)
                // The default (a simple field list model): a short field list has no picker chrome
                .story("StaticFieldsHaveNoPickerChrome", context -> editor(context, populatedExpression(), false))
                .withPlay(play -> {
                    play.click(selectTermFieldPicker(play, "UserId"));
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                    // Stroom hides the pager and the quick filter rather than omitting them
                    play.expect(play.screen().querySelector(".selection-list .pager-panel__bar")).not().toBeVisible();
                    play.expect(play.screen().querySelector(".selection-list__filter")).not().toBeVisible();
                })
                // A field list served by a data source always offers its quick filter and pager
                .story("DynamicFieldsAlwaysShowPickerChrome", context -> editor(context, populatedExpression(), true))
                .withPlay(play -> {
                    play.click(selectTermFieldPicker(play, "UserId"));
                    play.waitFor(() -> play.expect(play.screen().querySelector(".selection-list .pager-panel__bar"))
                            .toBeVisible());
                    play.expect(play.screen().querySelector(".selection-list__filter")).toBeVisible();
                })
                // Clicking the drop-down arrow opens the picker, as clicking its label does
                .story("ClickingTheDropdownArrowOpensThePicker",
                        context -> editor(context, populatedExpression(), false))
                .withPlay(play -> {
                    selectTermFieldPicker(play, "UserId");
                    // The arrow is the SelectionBox's IconBox icon
                    final Query arrowSvgPath = play.querySelector(FIELD_PICKER + " .icon-box__icon svg path");
                    play.expect(arrowSvgPath).not().toBeNull();
                    play.click(arrowSvgPath);
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                })
                // Add Term adds a term with no field
                .story("AddTermStartsWithNoField", context -> editor(context, emptyExpression(), false))
                .withPlay(play -> {
                    play.click(play.getByTitle("Add Term"));
                    // The field box's painted value is the SelectionBox's render box
                    final Query picker = play.querySelector(FIELD_PICKER + " .selection-box__value");
                    play.waitFor(() -> play.expect(picker).not().toBeNull());
                    play.expect(picker.textContent()).toBe("");
                    for (final QueryField field : fields()) {
                        play.expect(picker).not().toHaveTextContent(field.getFldName());
                    }
                    // And no value editor while the term has no field.
                    // Stroom's term editor keeps its value box, hidden
                    play.expect(play.querySelector(".term-editor__item--wide")).not().toBeVisible();
                })
                // A field list served by a data source is only fetched when needed
                .story("AsyncFieldSourceFetchesOnlyWhenNeeded",
                        context -> editor(context, populatedExpression(), true))
                .withPlay(ExpressionBuilderStories::playAsyncFieldSource);
    }

    private static void playToolbar(final Play play) {
        for (final String title : TOOLBAR) {
            final Query button = play.getByTitle(title);
            play.expect(button.property("tagName")).toBe("BUTTON");
            play.expect(button.className()).toMatch("toolbar-button");
            // The toolbar is one tab stop (its first button); the arrow keys reach the others
            play.expect(button.property("tabIndex")).toBe(title.equals(TOOLBAR.get(0))
                    ? 0
                    : -1);
        }
        // Nothing is selected yet, so the selection's buttons are disabled and the others not
        play.expect(play.getByTitle("Add Term")).not().toHaveAttribute("aria-disabled");
        // Disabled with aria-disabled, not the disabled attribute, so it can still be reached
        play.expect(play.getByTitle(DUPLICATE)).toHaveAttribute("aria-disabled", "true");
        play.expect(play.getByTitle("Add Term")).not().toHaveAttribute("disabled");
        // Focusable means focusable
        final Query addTerm = play.getByTitle("Add Term");
        play.run("focus Add Term", () -> addTerm.element().get().focus());
        play.expect(addTerm).toHaveFocus();
        play.keyboard("{ArrowRight}");
        play.expect(play.getByTitle("Add Operator")).toHaveFocus();
    }

    private static void playAsyncFieldSource(final Play play) {
        // Terms are on screen, as labels. Nothing has been asked of the server.
        play.waitFor(() -> play.expect("labels", () -> play.querySelectorAll(".expression-item__label")
                .count().get()).toBeGreaterThan(0));
        play.expect(play.spy(FIELD_SOURCE)).not().toHaveBeenCalled();

        // Selecting a term finds that term's field by name, not the list
        final Query picker = selectTermFieldPicker(play, "UserId");
        play.waitFor(() -> play.expect(play.spy(FIELD_SOURCE)).toHaveBeenCalledWith("findFieldByName:UserId"));
        play.expect(play.spy(FIELD_SOURCE)).not().toHaveBeenCalledWith("loadFields");

        // Opening the picker fetches the list
        play.click(picker);
        play.waitFor(() -> play.expect(play.spy(FIELD_SOURCE)).toHaveBeenCalledWith("loadFields"));
        play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
        play.waitFor(() -> play.expect(play.screen().getByText("EventTime", ".selection-item-cell__text"))
                .toBeInTheDocument());
        // A data source's list always has the quick filter
        play.expect(play.screen().querySelector(".selection-list__filter")).toBeVisible();
    }

    /// Selects the term whose label contains the text, and returns its field picker (the part of
    /// Stroom's SelectionBox that takes clicks).
    private static Query selectTermFieldPicker(final Play play, final String label) {
        final Query term = play.findByText(TextMatch.containing(label), ".expression-item__label");
        play.click(term);
        // The field picker is a Stroom SelectionBox, whose text box takes the clicks
        final Query picker = play.querySelector(FIELD_PICKER + " .selection-box__input");
        play.waitFor(() -> play.expect(picker).not().toBeNull());
        return picker;
    }

    /// The stories' fields. The text fields have the conditions of Stroom's SQL backed text fields
    /// (`SQL_TEXT`), which offer `Contains`, unlike Stroom's default text conditions. RunAsUser is a
    /// user field (`createUserRef`).
    private static List<QueryField> fields() {
        return List.of(
                textField("UserId"),
                textField("Description"),
                QueryField.createDate("EventTime"),
                // An ID, as Stroom's StreamId is (whose conditions include "in")
                QueryField.createId("StreamId"),
                QueryField.createDocRefByUniqueName("Feed", "Feed"),
                QueryField.createUserRef("RunAsUser"));
    }

    private static QueryField textField(final String name) {
        return QueryField.builder()
                .fldName(name)
                .fldType(FieldType.TEXT)
                .conditionSet(ConditionSet.SQL_TEXT)
                .queryable(true)
                .build();
    }

    /// `UserId contains admin AND (EventTime > … OR Feed is TEST_FEED) AND RunAsUser is Alice`, the
    /// last disabled: one term for each kind of value editor.
    private static ExpressionOperator populatedExpression() {
        return ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(ExpressionTerm.builder()
                        .field("UserId")
                        .condition(Condition.CONTAINS)
                        .value("admin")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(ExpressionTerm.builder()
                                .field("EventTime")
                                .condition(Condition.GREATER_THAN)
                                .value("2024-01-01T00:00:00.000Z")
                                .build())
                        .addTerm(ExpressionTerm.builder()
                                .field("Feed")
                                .condition(Condition.IS_DOC_REF)
                                .value("TEST_FEED")
                                .docRef(new DocRef("Feed", "feed-uuid-1", "TEST_FEED"))
                                .build())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .enabled(false)
                        .field("RunAsUser")
                        .condition(Condition.IS_USER_REF)
                        .value("Alice Anderson")
                        .docRef(new DocRef("User", "u-alice", "Alice Anderson"))
                        .build())
                .build();
    }

    /// `UserId contains admin AND (EventTime between … OR (Feed is TEST_FEED AND StreamId > 1000 AND
    /// NOT (Description contains test OR Description = sample)) OR StreamId in 1, 2, 3) AND NOT
    /// UserId = system AND RunAsUser is Alice`, the NOT operator before the user and the user term
    /// disabled.
    private static ExpressionOperator complexExpression() {
        return ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(ExpressionTerm.builder()
                        .field("UserId")
                        .condition(Condition.CONTAINS)
                        .value("admin")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(ExpressionTerm.builder()
                                .field("EventTime")
                                .condition(Condition.BETWEEN)
                                .value("2024-01-01T00:00:00.000Z,2024-02-01T00:00:00.000Z")
                                .build())
                        .addOperator(ExpressionOperator.builder()
                                .op(Op.AND)
                                .addTerm(ExpressionTerm.builder()
                                        .field("Feed")
                                        .condition(Condition.IS_DOC_REF)
                                        .value("TEST_FEED")
                                        .docRef(new DocRef("Feed", "feed-uuid-1", "TEST_FEED"))
                                        .build())
                                .addTerm(ExpressionTerm.builder()
                                        .field("StreamId")
                                        .condition(Condition.GREATER_THAN)
                                        .value("1000")
                                        .build())
                                .addOperator(ExpressionOperator.builder()
                                        .op(Op.NOT)
                                        .addTerm(ExpressionTerm.builder()
                                                .field("Description")
                                                .condition(Condition.CONTAINS)
                                                .value("test")
                                                .build())
                                        .addTerm(ExpressionTerm.builder()
                                                .field("Description")
                                                .condition(Condition.EQUALS)
                                                .value("sample")
                                                .build())
                                        .build())
                                .build())
                        .addTerm(ExpressionTerm.builder()
                                .field("StreamId")
                                .condition(Condition.IN)
                                .value("1,2,3")
                                .build())
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .enabled(false)
                        .addTerm(ExpressionTerm.builder()
                                .field("UserId")
                                .condition(Condition.EQUALS)
                                .value("system")
                                .build())
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .enabled(false)
                        .field("RunAsUser")
                        .condition(Condition.IS_USER_REF)
                        .value("Alice Anderson")
                        .docRef(new DocRef("User", "u-alice", "Alice Anderson"))
                        .build())
                .build();
    }

    // An item of the expression tree, by its name
    private static Query item(final Play play, final String name) {
        return play.getByRole("treeitem", TextMatch.exact(name));
    }

    // An operator of the expression tree, by the start of its name (which says how many items it
    // has, and whether it can take an item being moved)
    private static Query operator(final Play play, final String start) {
        return play.getByRole("treeitem", TextMatch.startingWith(start));
    }

    // Presses on an item (by the start of its name) and moves past the drag threshold, lifting it
    // out of the tree
    private static void liftItem(final Play play, final Play tree, final String name) {
        final Query item = play.getByRole("treeitem", TextMatch.startingWith(name));
        play.fireEvent().mouseDown(item, EventInit.create().atCentreOf(item).buttons(1));
        play.fireEvent().mouseMove(item, EventInit.create().relativeTo(item, 60, 40).buttons(1));
        play.waitFor(() -> play.expect(tree.queryByText(name)).toBeNull());
    }

    // EventTime is back where it was, the OR's first item, with nothing marked
    private static void expectEventTimeBack(final Play play, final Play tree) {
        play.waitFor(() -> play.expect(tree.queryByText(EVENT_TIME)).toBeInTheDocument());
        play.waitFor(() -> play.expect(item(play, EVENT_TIME)).toHaveAttribute("aria-level", "3"));
        play.expect(item(play, EVENT_TIME)).toHaveAttribute("aria-posinset", "1");
        play.expect(play.querySelectorAll(MARKED)).toHaveLength(0);
    }

    private static double scrollLeft(final Query element) {
        return element.element().get().getScrollLeft();
    }

    private static double scrollLeftMax(final Query element) {
        return element.element().get().getScrollWidth() - element.element().get().getClientWidth();
    }

    private static ExpressionOperator emptyExpression() {
        return ExpressionOperator.builder().op(Op.AND).build();
    }

    private static ScreenHarness harness(final StoryContext context) {
        return harness(context, ContentStorySupport::decorate);
    }

    // The stories' services; `decorate` answers Stroom's check that a term's document exists (and
    // its current name)
    private static ScreenHarness harness(final StoryContext context, final RestHandler decorate) {
        final UserFixture users = UserFixture.fixtureUsers();
        final FieldFixture fieldFixture = new FieldFixture(fields());
        final ScreenHarness[] holder = new ScreenHarness[1];
        final ScreenHarness harness = ScreenHarness.create(context, TreeFixtures.explorerRoutes(
                        TreeFixtures.fixtureTree())
                .post(TreeFixtures.DECORATE, decorate)
                .post(UserFixture.FIND, request -> RestReply.json(users.find(request.getBody())))
                // Typing a value asks for suggestions
                .post("/suggest/v1", RestReply.json("{\"list\": []}"))
                .post(FieldFixture.FIND_FIELDS, request -> {
                    holder[0].spy(FIELD_SOURCE, FieldFixture.describe(request.getBody()));
                    return RestReply.json(fieldFixture.find(request.getBody()));
                })
                .build());
        holder[0] = harness;
        // Records the field list's requests
        harness.fn(FIELD_SOURCE);
        return harness;
    }

    /// The editor in the stories' frame.
    private static Widget editor(final StoryContext context,
                                 final ExpressionOperator expression,
                                 final boolean dynamicFields) {
        return editor(context, expression, dynamicFields, ContentStorySupport::decorate);
    }

    /// The editor in the stories' frame, with Stroom's check that a document exists answered as
    /// given.
    private static Widget editor(final StoryContext context,
                                 final ExpressionOperator expression,
                                 final boolean dynamicFields,
                                 final RestHandler decorate) {
        final ScreenHarness harness = harness(context, decorate);
        final ExpressionTreeController tree = treeController(harness);
        final ExpressionEditorController controller = new ExpressionEditorController(harness.getEventBus(), tree)
                .attach(new ExpressionEditor());
        harness.own(controller);
        controller.init(DATA_SOURCE, fieldModel(harness, dynamicFields));
        controller.read(expression);
        final Spy onChange = context.fn(ON_CHANGE);
        controller.setOnChange(() -> onChange.call(controller.write().toString()));
        harness.add(frame(controller.asWidget()));
        return harness.asWidget();
    }

    private static Widget readOnly(final StoryContext context) {
        final ScreenHarness harness = harness(context);
        final ExpressionTreeController tree = treeController(harness).attach(new ExpressionTree());
        harness.own(tree);
        // As Stroom's read only views of an expression (e.g. ProcessorsController) do
        tree.setSelectionModel(null);
        tree.init(DATA_SOURCE, fieldModel(harness, false));
        tree.read(populatedExpression());
        harness.add(frame(tree.asWidget()));
        return harness.asWidget();
    }

    /// Stroom's expression editor ([ExpressionEditorController]) showing these stories' expression
    /// (whose last term is disabled), for the view states sheet. Its fields come from a fixed list,
    /// so the harness needs no fixtures.
    ///
    /// @param harness  The harness it is shown in.
    /// @param readOnly Whether the expression is read only.
    /// @return The editor's widget.
    public static Widget expressionEditor(final ScreenHarness harness, final boolean readOnly) {
        final ExpressionTreeController tree = treeController(harness);
        final ExpressionEditorController controller = new ExpressionEditorController(harness.getEventBus(), tree)
                .attach(new ExpressionEditor());
        harness.own(controller);
        controller.init(DATA_SOURCE, fieldModel(harness, false));
        controller.setReadOnly(readOnly);
        controller.read(populatedExpression());
        return controller.asWidget();
    }

    private static ExpressionTreeController treeController(final ScreenHarness harness) {
        return new ExpressionTreeController(harness.getEventBus(), () ->
                new TermEditorController(harness.getEventBus(),
                        harness.getRestFactory(),
                        harness.getUiConfigCache(),
                        SelectorWidgets.docSelectionControllerProvider(harness),
                        SelectorWidgets.userRefSelectionControllerProvider(harness),
                        DateTimeWidgets.dateTimeBoxControllers(harness)));
    }

    private static FieldSelectionListModel fieldModel(final ScreenHarness harness, final boolean dynamic) {
        if (dynamic) {
            final DynamicFieldSelectionListModel model = new DynamicFieldSelectionListModel(harness.getEventBus(),
                    new DataSourceClient(harness.getRestFactory()));
            model.setDataSourceRefConsumer(consumer -> consumer.accept(DATA_SOURCE));
            return model;
        }
        final SimpleFieldSelectionListModel model = new SimpleFieldSelectionListModel();
        model.addItems(fields());
        return model;
    }

    /// The stories' frame around the editor.
    private static Widget frame(final Widget editor) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "760px");
        style.setProperty("height", "420px");
        style.setProperty("border", "1px solid var(--border-color)");
        editor.setSize("100%", "100%");
        frame.add(editor);
        return frame;
    }
}
