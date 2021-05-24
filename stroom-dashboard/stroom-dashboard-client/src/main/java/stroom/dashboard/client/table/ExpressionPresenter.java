/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.query.api.v2.Field;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.EqualsUtil;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.SimpleMenuItem;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ExpressionPresenter extends MyPresenterWidget<ExpressionPresenter.ExpressionView>
        implements ExpressionUiHandlers {
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private static final int DEFAULT_COMPLETION_SCORE = 300; // Not sure what the range of scores is
    private static final String EXPRESSIONS_HELP_BASE_PATH = "/user-guide/dashboards/expressions";

    private final MenuListPresenter menuListPresenter;
    private final RestFactory restFactory;
    private final EditorPresenter editorPresenter;
    private final List<AceCompletion> functionCompletions = new ArrayList<>();
    private final UiConfigCache clientPropertyCache;
    private AceCompletionProvider functionsCompletionProvider;
    private List<Item> functionsMenuItems;
    private List<Item> fieldsMenuItems;
    private TablePresenter tablePresenter;
    private Field field;
    private BiConsumer<Field, Field> fieldChangeConsumer;

    @Inject
    public ExpressionPresenter(final EventBus eventBus,
                               final ExpressionView view,
                               final MenuListPresenter menuListPresenter,
                               final RestFactory restFactory,
                               final EditorPresenter editorPresenter,
                               final UiConfigCache clientPropertyCache) {
        super(eventBus, view);
        this.menuListPresenter = menuListPresenter;
        this.restFactory = restFactory;
        this.editorPresenter = editorPresenter;
        this.clientPropertyCache = clientPropertyCache;
        view.setUiHandlers(this);
        view.setEditor(editorPresenter.getView());

        final ButtonView addFunctionButton = view.addButton(SvgPresets.FUNCTION
                .title("Insert Function"));
        final ButtonView addFieldButton = view.addButton(SvgPresets.FIELD
                .title("Insert Field"));
        final ButtonView helpButton = view.addButton(SvgPresets.HELP);

        buildMenusAndCompletions();

        addFunctionButton.addClickHandler(this::onAddFunction);
        addFieldButton.addClickHandler(this::onAddField);
        helpButton.addClickHandler(this::onShowHelp);
    }

    private void buildMenusAndCompletions() {
        withHelpUrl(helpUrl -> {
            final Rest<List<FunctionSignature>> rest = restFactory.create();
            rest
                    .onSuccess(functions -> {
                        functionCompletions.clear();
                        functionsMenuItems = FunctionSignatureUtil.buildMenuItems(
                                functions,
                                this::addFunction,
                                helpUrl);
                        // addAll rather than assignment due to .js closure scope
                        functionCompletions.addAll(FunctionSignatureUtil.buildCompletions(
                                functions,
                                helpUrl));
                        functionsCompletionProvider = buildCompletionProvider(functionCompletions);

                        editorPresenter.registerCompletionProviders(
                                buildCompletionProvider(functionCompletions),
                                buildFieldsCompletionProvider());
                    })
                    .onFailure(throwable -> {
                        AlertEvent.fireError(
                                ExpressionPresenter.this,
                                throwable.getMessage(),
                                null);
                    })
                    .call(DASHBOARD_RESOURCE)
                    .fetchFunctions();
        });
    }

    private void onShowHelp(final ClickEvent clickEvent) {
        if ((clickEvent.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            withHelpUrl(helpUrl -> {
                String url = helpUrl + EXPRESSIONS_HELP_BASE_PATH;

                Window.open(url, "_blank", "");
            });
        }
    }

    private void withHelpUrl(final Consumer<String> work) {
        clientPropertyCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrl();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        work.accept(helpUrl);
                    } else {
                        AlertEvent.fireError(
                                ExpressionPresenter.this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        ExpressionPresenter.this,
                        caught.getMessage(),
                        null));
    }

    private AceCompletionProvider buildFieldsCompletionProvider() {

        final List<AceCompletion> fieldCompletions;
        if (tablePresenter != null && tablePresenter.getIndexFieldsMap() != null) {
            fieldCompletions = tablePresenter.getIndexFieldsMap()
                    .keySet()
                    .stream()
                    .map(fieldName -> {
                        final String fieldExpression = "${" + fieldName + "}";
                        final String snippet = "\\" + fieldExpression + "${0}"; // escape our $ for snippet engine
                        return new AceCompletionSnippet(
                                fieldExpression,
                                snippet,
                                DEFAULT_COMPLETION_SCORE,
                                "Field",
                                SafeHtmlUtils.htmlEscape(fieldExpression));
                    })
                    .collect(Collectors.toList());
        } else {
            fieldCompletions = Collections.emptyList();
        }

        return buildCompletionProvider(fieldCompletions);
    }

    private AceCompletionProvider buildCompletionProvider(List<AceCompletion> completions) {

        final AceCompletion[] completionsArr = completions.toArray(
                new AceCompletion[completions.size()]);

        return (editor, pos, prefix, callback) -> {
            callback.invokeWithCompletions(completionsArr);
        };
    }

    private void setupEditor() {
        editorPresenter.setMode(AceEditorMode.STROOM_EXPRESSION);
        editorPresenter.setReadOnly(false);

        // Need to explicitly set some of these as the defaults don't
        // seem to work, maybe due to timing
        editorPresenter.getLineNumbersOption().setOff();
        editorPresenter.getLineWrapOption().setOn();
        editorPresenter.getHighlightActiveLineOption().setOff();
        editorPresenter.getBasicAutoCompletionOption().setOn();
        editorPresenter.getSnippetsOption().setOn();

        fieldsMenuItems = createFieldsMenuItems();
    }

    public void show(final TablePresenter tablePresenter,
                     final Field field,
                     final BiConsumer<Field, Field> fieldChangeConsumer) {
        this.tablePresenter = tablePresenter;
        this.field = field;
        this.fieldChangeConsumer = fieldChangeConsumer;

        if (field.getExpression() != null) {
            editorPresenter.setText(field.getExpression());
        } else {
            editorPresenter.setText("");
        }

        final PopupSize popupSize = new PopupSize(
                700,
                300,
                300,
                300,
                true);

        ShowPopupEvent.fire(
                tablePresenter,
                this,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Set Expression For '" + field.getName() + "'",
                this);

        Scheduler.get().scheduleDeferred(editorPresenter::focus);

        // If this is done without the scheduler then we get weired behaviour when you click
        // in the text area if line wrap is set to on.  If it is initially set to off and the user
        // manually sets it to on all is fine. Confused.
        Scheduler.get().scheduleDeferred(this::setupEditor);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String expression = editorPresenter.getText();
            if (EqualsUtil.isEquals(expression, field.getExpression())) {
                HidePopupEvent.fire(tablePresenter, this);
            } else {
                if (expression == null) {
                    fieldChangeConsumer.accept(field, field.copy().expression(null).build());
                    HidePopupEvent.fire(tablePresenter, this);
                } else {
                    // Check the validity of the expression.
                    final Rest<ValidateExpressionResult> rest = restFactory.create();
                    rest
                            .onSuccess(result -> {
                                if (result.isOk()) {
                                    fieldChangeConsumer.accept(field, field.copy().expression(expression).build());
                                    HidePopupEvent.fire(tablePresenter, ExpressionPresenter.this);
                                } else {
                                    AlertEvent.fireError(tablePresenter, result.getString(), null);
                                }
                            })
                            .call(DASHBOARD_RESOURCE)
                            .validateExpression(expression);
                }
            }
        } else {
            HidePopupEvent.fire(tablePresenter, this);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        editorPresenter.deRegisterCompletionProviders();
    }

    @Override
    public void onAddFunction(final ClickEvent event) {
        showMenu(event, functionsMenuItems);
    }

    public void showMenu(final ClickEvent event, final List<Item> menuItems) {
        if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(ExpressionPresenter.this, menuListPresenter);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                }
            };

            final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
            final PopupPosition popupPosition = new PopupPosition(
                    target.getAbsoluteLeft() - 3,
                    target.getAbsoluteTop() + target.getClientHeight() + 1);

            menuListPresenter.setData(menuItems);

            ShowPopupEvent.fire(
                    this,
                    menuListPresenter,
                    PopupType.POPUP,
                    popupPosition,
                    popupUiHandlers);
        }
    }

    @Override
    public void onAddField(final ClickEvent event) {
        showMenu(event, fieldsMenuItems);
    }

    private List<Item> createFieldsMenuItems() {

        final List<Item> menuItems;
        if (tablePresenter != null && tablePresenter.getIndexFieldsMap() != null) {
            final AtomicInteger position = new AtomicInteger(0);
            menuItems = tablePresenter.getIndexFieldsMap()
                    .keySet()
                    .stream()
                    .sorted(Comparator.comparing(String::toLowerCase))
                    .map(fieldName ->
                            "${" + fieldName + "}")
                    .map(fieldName ->
                            new SimpleMenuItem(
                                    position.getAndIncrement(),
                                    fieldName,
                                    null,
                                    true,
                                    () -> addField(fieldName)))
                    .collect(Collectors.toList());
        } else {
            menuItems = Collections.emptyList();
        }

        return menuItems;
    }

    private void addField(final String field) {
        editorPresenter.insertTextAtCursor(field);
        editorPresenter.focus();
    }

    private void addFunction(final String func) {
        // will insert if there is no selection or replace if there is
        editorPresenter.insertSnippet(func);
        editorPresenter.focus();
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface ExpressionView extends View, HasUiHandlers<ExpressionUiHandlers> {

        void setEditor(final EditorView editor);

        ButtonView addButton(final Preset preset);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public enum FunctionDef {
        // TODO @AT This could do with being auto-generated from annotations on the actual
        //   functions in stroom-expression, or maybe each AbstractFunction in stroom-expression
        //   should have static getName(), getDescription(), getArgs(), etc. then we would not need
        //   this enum.
        // Aggregate functions
        AVERAGE("average", "The mean of all values in the group.",
                "field"),
        COUNT("count", "The count of all values in the group."),
        COUNT_GROUPS("countGroups", ""),
        COUNT_UNIQUE("countUnique", "", "field"),
        JOINING("joining", "", "field", "delimiter", "limit"),
        MAX("max", "", "field"),
        MIN("min", "", "field"),
        ST_DEV("stDev", "", "field"),
        SUM("sum", "", "field"),
        VARIANCE("variance", "", "field"),

        // Cast functions
        TO_BOOLEAN("toBoolean", "Case the value to a boolean type.",
                "value");

        private final String name;
        private final List<String> args;
        private final String description;

        FunctionDef(final String name, final String description, final String... args) {
            this.name = name;
            this.args = Arrays.asList(args);
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public List<String> getArgs() {
            return args;
        }

        public String getDescription() {
            return description;
        }

        public String toString() {
            // i.e. concat(value1, value2)
            return name +
                    "(" +
                    String.join(", ", args) +
                    ")";
        }
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface LeafCommandBuilder {

        Command apply(final FunctionDef functionDef,
                      final Ancestors ancestors);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static class Ancestors {
        private final List<String> ancestors;

        public Ancestors() {
            this.ancestors = new ArrayList<>();
        }

        public Ancestors(final Ancestors ancestors) {
            this.ancestors = new ArrayList<>(ancestors.ancestors);
        }

        public Optional<String> getImmediateParent() {
            if (ancestors.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(ancestors.get(ancestors.size() - 1));
            }
        }

        public void add(final String parent) {
            ancestors.add(parent);
        }

        public List<String> getAncestors() {
            return ancestors;
        }

        public String getAncestry(final String delimiter) {
            return String.join(delimiter, ancestors);
        }
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * TODO Once we have function definitions (name, desc, args) on the actual functions in
     * stroom-expression we need to reinstate a variant of this to build the menu.
     */
    public static class MenuBuilder {

        private final List<Item> items = new ArrayList<>();
        private final Ancestors ancestors;

        public MenuBuilder() {
            ancestors = new Ancestors();
        }

        private MenuBuilder(final Ancestors ancestors) {
            this.ancestors = new Ancestors(ancestors);
        }

        public MenuBuilder addBranch(final String text,
                                     final Consumer<MenuBuilder> branchBuilder) {
            GWT.log("Adding branch " + text);
            final Ancestors childsAncestors = new Ancestors(ancestors);
            childsAncestors.add(text);
            final MenuBuilder childMenuBuilder = new MenuBuilder(childsAncestors);
            branchBuilder.accept(childMenuBuilder);
            final List<Item> childItems = childMenuBuilder.build();

            items.add(new SimpleParentMenuItem(items.size(), text, childItems));
            return this;
        }

        public MenuBuilder addLeaf(final FunctionDef functionDef,
                                   final BiFunction<FunctionDef, Ancestors, Command> commandBuilder) {

            final Command command = commandBuilder.apply(functionDef, ancestors);
            items.add(new SimpleMenuItem(items.size(), functionDef.toString(), null, true, command));
            return this;
        }

        public List<Item> build() {
            return items;
        }
    }
}
