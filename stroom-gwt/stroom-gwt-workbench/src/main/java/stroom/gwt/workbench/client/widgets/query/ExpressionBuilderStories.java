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

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.client.view.EditExpressionViewImpl;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.selectors.SelectorWidgets;
import stroom.gwt.workbench.client.widgets.selectors.UserFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
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
import stroom.query.client.ExpressionTreePresenter;
import stroom.query.client.ExpressionTreeViewImpl;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.query.client.presenter.SimpleFieldSelectionListModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for Stroom's [EditExpressionPresenter] (an [ExpressionTreePresenter] under a toolbar),
/// matching `Widgets/Query/ExpressionBuilder` in the React Storybook: the editor of a query
/// expression, a tree of operators and terms, each term edited (when selected) with a field
/// picker, a condition and a value editor for the field's type.
///
/// The term editors' document and user pickers are Stroom's (see [SelectorWidgets]), answered by
/// the React stories' explorer tree and users. A field list served by a data source (React's
/// `dynamicFields`/`fieldSource`) is Stroom's `DynamicFieldSelectionListModel`, answered by a
/// [FieldFixture].
public final class ExpressionBuilderStories {

    private static final String ON_CHANGE = "onChange";
    private static final String FIELD_SOURCE = "fieldSource";
    private static final List<String> TOOLBAR = List.of("Add Term", "Add Operator", "Copy", "Disable", "Delete");
    private static final String POPUP = ".SelectionPopup";
    // The field picker of the selected term (a Stroom SelectionBox), and its parts
    private static final String FIELD_PICKER = ".termEditor-item.field";
    private static final DocRef DATA_SOURCE = new DocRef("Index", "index-uuid-1", "Example Index");

    private ExpressionBuilderStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's loadSuggestions (term value autocomplete) is Stroom's suggestion service, which
        // the stories don't answer (no suggestions are shown); onSearch has no equivalent in the
        // editor (Stroom's query screens search).
        registry.component("Widgets/Query/ExpressionBuilder", ExpressionBuilderStories.class)
                .layout(StoryLayout.CENTERED)
                // A populated expression. Select a node to edit it inline; add, copy, disable or
                // delete with the toolbar, or drag a node onto an operator to move it.
                .story("Populated", context -> editor(context, populatedExpression(), false))
                // A new, empty expression (just the root AND)
                .story("NewExpression", context -> editor(context, emptyExpression(), false))
                // Read only - the populated expression, with no selection, editing or dragging
                .story("ReadOnly", ExpressionBuilderStories::readOnly)
                .withPlay(play -> {
                    // Differs from React: Stroom shows a read only expression (e.g. a processor
                    // filter's, or a query in the history) as the tree alone, with no toolbar,
                    // rather than a toolbar of disabled buttons
                    play.findAllByText(TextMatch.containing("UserId"), ".expressionItemBox-label");
                    for (final String title : TOOLBAR) {
                        play.expect(play.queryByTitle(title)).toBeNull();
                    }
                    // Nothing can be selected
                    play.click(play.getByText(TextMatch.containing("UserId"), ".expressionItemBox-label"));
                    play.expect(play.querySelector(FIELD_PICKER)).toBeNull();
                })
                // The toolbar is made of real buttons (Stroom's SvgButton, an InlineSvgButton)
                .story("ToolbarIsRealButtons", context -> editor(context, populatedExpression(), false))
                .withPlay(ExpressionBuilderStories::playToolbar)
                // The default (a simple field list model): a short field list has no picker chrome
                .story("StaticFieldsHaveNoPickerChrome", context -> editor(context, populatedExpression(), false))
                .withPlay(play -> {
                    play.click(selectTermFieldPicker(play, "UserId"));
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                    // Stroom hides the pager and the quick filter rather than omitting them
                    play.expect(play.screen().querySelector(".selectionList .pager")).not().toBeVisible();
                    play.expect(play.screen().querySelector(".selectionList-quickFilter")).not().toBeVisible();
                })
                // A field list served by a data source always offers its quick filter and pager
                .story("DynamicFieldsAlwaysShowPickerChrome", context -> editor(context, populatedExpression(), true))
                .withPlay(play -> {
                    play.click(selectTermFieldPicker(play, "UserId"));
                    play.waitFor(() -> play.expect(play.screen().querySelector(".selectionList .pager")).toBeVisible());
                    play.expect(play.screen().querySelector(".selectionList-quickFilter")).toBeVisible();
                })
                // Clicking the drop-down arrow opens the picker, as clicking its label does
                .story("ClickingTheDropdownArrowOpensThePicker",
                        context -> editor(context, populatedExpression(), false))
                .withPlay(play -> {
                    selectTermFieldPicker(play, "UserId");
                    // Differs from React: the arrow is the SelectionBox's SvgIconBox icon
                    final Query arrowSvgPath = play.querySelector(FIELD_PICKER + " .svgIconBox-icon-outer svg path");
                    play.expect(arrowSvgPath).not().toBeNull();
                    play.click(arrowSvgPath);
                    play.waitFor(() -> play.expect(play.screen().querySelector(POPUP)).not().toBeNull());
                })
                // Add Term adds a term with no field
                .story("AddTermStartsWithNoField", context -> editor(context, emptyExpression(), false))
                .withPlay(play -> {
                    play.click(play.getByTitle("Add Term"));
                    // Differs from React: the field box's painted value is the SelectionBox's
                    // render box
                    final Query picker = play.querySelector(FIELD_PICKER + " .SelectionBox-renderBox");
                    play.waitFor(() -> play.expect(picker).not().toBeNull());
                    play.expect(picker.textContent()).toBe("");
                    for (final QueryField field : fields()) {
                        play.expect(picker).not().toHaveTextContent(field.getFldName());
                    }
                    // And no value editor while the term has no field.
                    // Differs from React: Stroom's term editor keeps its value box, hidden
                    play.expect(play.querySelector(".termEditor-item.wide")).not().toBeVisible();
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
            play.expect(button.className()).toMatch("inline-svg-button");
            play.expect(button.property("tabIndex")).toBe(0);
        }
        // Nothing is selected yet, so the selection's buttons are disabled and the others not
        play.expect(play.getByTitle("Add Term").className()).not().toMatch("disabled");
        play.expect(play.getByTitle("Copy").className()).toMatch("disabled");
        // Differs from React: Stroom's InlineSvgButton.setEnabled(false) also sets the native
        // `disabled` property (GWT's FocusWidget), as well as the `disabled` class
        play.expect(play.getByTitle("Copy")).toHaveAttribute("aria-disabled", "true");
        play.expect(play.getByTitle("Add Term")).not().toHaveAttribute("disabled");
        // Focusable means focusable
        final Query addTerm = play.getByTitle("Add Term");
        play.run("focus Add Term", () -> addTerm.element().get().focus());
        play.expect(addTerm).toHaveFocus();
    }

    private static void playAsyncFieldSource(final Play play) {
        // Terms are on screen, as labels. Nothing has been asked of the server.
        play.waitFor(() -> play.expect("labels", () -> play.querySelectorAll(".expressionItemBox-label")
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
        play.waitFor(() -> play.expect(play.screen().getByText("EventTime", ".selectionItemCell-text"))
                .toBeInTheDocument());
        // A data source's list always has the quick filter
        play.expect(play.screen().querySelector(".selectionList-quickFilter")).toBeVisible();
    }

    /// Selects the term whose label contains the text, and returns its field picker (the part of
    /// Stroom's SelectionBox that takes clicks).
    private static Query selectTermFieldPicker(final Play play, final String label) {
        final Query term = play.findByText(TextMatch.containing(label), ".expressionItemBox-label");
        play.click(term);
        // Differs from React: the field picker is a Stroom SelectionBox, whose text box takes the
        // clicks (React's `.selection-box__display`)
        final Query picker = play.querySelector(FIELD_PICKER + " .SelectionBox-textBox");
        play.waitFor(() -> play.expect(picker).not().toBeNull());
        return picker;
    }

    /// The React stories' FIELDS. React's text fields offer `Contains`, which Stroom's default
    /// text conditions don't, so they have the conditions of Stroom's SQL backed text fields
    /// (`SQL_TEXT`). React's RunAsUser is a Text field limited to user conditions; in Stroom that
    /// is a user field (`createUserRef`).
    private static List<QueryField> fields() {
        return List.of(
                textField("UserId"),
                textField("Description"),
                QueryField.createDate("EventTime"),
                QueryField.createLong("StreamId"),
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

    private static ExpressionOperator emptyExpression() {
        return ExpressionOperator.builder().op(Op.AND).build();
    }

    private static ScreenHarness harness(final StoryContext context) {
        final UserFixture users = UserFixture.fixtureUsers();
        final FieldFixture fieldFixture = new FieldFixture(fields());
        final ScreenHarness[] holder = new ScreenHarness[1];
        final ScreenHarness harness = ScreenHarness.create(context, TreeFixtures.explorerRoutes(
                        TreeFixtures.fixtureTree())
                .post(UserFixture.FIND, request -> RestReply.json(users.find(request.getBody())))
                .post(FieldFixture.FIND_FIELDS, request -> {
                    holder[0].spy(FIELD_SOURCE, FieldFixture.describe(request.getBody()));
                    return RestReply.json(fieldFixture.find(request.getBody()));
                })
                .build());
        holder[0] = harness;
        // As React's fieldSource records its calls
        harness.fn(FIELD_SOURCE);
        return harness;
    }

    /// The editor in the React stories' frame.
    private static Widget editor(final StoryContext context,
                                 final ExpressionOperator expression,
                                 final boolean dynamicFields) {
        final ScreenHarness harness = harness(context);
        final ExpressionTreePresenter treePresenter = treePresenter(harness);
        final EditExpressionPresenter presenter = new EditExpressionPresenter(harness.getEventBus(),
                new EditExpressionViewImpl(GWT.create(EditExpressionViewImpl.Binder.class)),
                treePresenter);
        harness.unbindOnCleanUp(presenter);
        harness.unbindOnCleanUp(treePresenter);
        presenter.bind();
        treePresenter.bind();
        presenter.init(harness.getRestFactory(), DATA_SOURCE, fieldModel(harness, dynamicFields));
        presenter.read(expression);
        final Spy onChange = context.fn(ON_CHANGE);
        harness.addRegistration(presenter.addChangeHandler(() -> onChange.call(presenter.write().toString())));
        harness.add(frame(presenter.getWidget()));
        return harness.asWidget();
    }

    private static Widget readOnly(final StoryContext context) {
        final ScreenHarness harness = harness(context);
        final ExpressionTreePresenter treePresenter = treePresenter(harness);
        harness.unbindOnCleanUp(treePresenter);
        // As Stroom's read only views of an expression (e.g. ProcessorPresenter) do
        treePresenter.setSelectionModel(null);
        treePresenter.bind();
        treePresenter.init(harness.getRestFactory(), DATA_SOURCE, fieldModel(harness, false));
        treePresenter.read(populatedExpression());
        harness.add(frame(treePresenter.getWidget()));
        return harness.asWidget();
    }

    /// Stroom's expression editor ([EditExpressionPresenter]) showing these stories' expression
    /// (whose last term is disabled), for the view states sheet. Its fields come from a fixed list,
    /// so the harness needs no fixtures.
    ///
    /// @param harness  The harness it is shown in.
    /// @param readOnly Whether the expression is read only.
    /// @return The editor's widget.
    public static Widget expressionEditor(final ScreenHarness harness, final boolean readOnly) {
        final ExpressionTreePresenter treePresenter = treePresenter(harness);
        final EditExpressionPresenter presenter = new EditExpressionPresenter(harness.getEventBus(),
                new EditExpressionViewImpl(GWT.create(EditExpressionViewImpl.Binder.class)),
                treePresenter);
        harness.unbindOnCleanUp(presenter);
        harness.unbindOnCleanUp(treePresenter);
        presenter.bind();
        treePresenter.bind();
        presenter.init(harness.getRestFactory(), DATA_SOURCE, fieldModel(harness, false));
        presenter.setReadOnly(readOnly);
        presenter.read(populatedExpression());
        return presenter.getWidget();
    }

    private static ExpressionTreePresenter treePresenter(final ScreenHarness harness) {
        final ExpressionTreeViewImpl view = new ExpressionTreeViewImpl(
                SelectorWidgets.docSelectionBoxProvider(harness),
                SelectorWidgets.userRefSelectionBoxProvider(harness),
                harness.getUserPreferencesManager(),
                harness.getUiConfigCache());
        return new ExpressionTreePresenter(harness.getEventBus(), view);
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

    /// Equivalent of the React stories' `frame`.
    private static Widget frame(final Widget editor) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "760px");
        style.setProperty("height", "420px");
        style.setProperty("border", "1px solid var(--panel__border-color, #ccc)");
        editor.setSize("100%", "100%");
        frame.add(editor);
        return frame;
    }
}
