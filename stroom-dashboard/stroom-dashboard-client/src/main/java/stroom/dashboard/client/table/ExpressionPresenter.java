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
import stroom.dashboard.shared.FunctionDefinition;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.query.api.v2.Field;
import stroom.svg.client.SvgPreset;
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
import stroom.widget.tooltip.client.presenter.TooltipUtil;

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
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionValue;
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
        final Rest<List<FunctionDefinition>> rest = restFactory.create();
        rest
                .onSuccess(functions -> {
//                    if (functionsMenuItems == null) {
                    functionCompletions.clear();
//                    functionsMenuItems = buildFunctionMenuItems(functions);
                    functionsMenuItems = FunctionDefinitionUtil.buildMenuItems(functions, this::addFunction);
//                    functionsMenuItems = createFunctionsMenuItemsAndSnippets();
//                    }
//                    functionsCompletionProvider = buildCompletionProvider(functionCompletions);
                    functionsCompletionProvider = buildCompletionProvider(Collections.emptyList());
                })
                .call(DASHBOARD_RESOURCE)
                .fetchFunctions();
    }

    private void onShowHelp(final ClickEvent clickEvent) {

        if ((clickEvent.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            clientPropertyCache.get()
                    .onSuccess(result -> {
                        final String helpUrl = result.getHelpUrl();
                        if (helpUrl != null && helpUrl.trim().length() > 0) {
                            String url = helpUrl + EXPRESSIONS_HELP_BASE_PATH;

                            Window.open(url, "_blank", "");
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
    }

//    private void withHelpUrl(final Consumer<String> work) {
//        clientPropertyCache.get()
//                .onSuccess(result -> {
//                    final String helpUrl = result.getHelpUrl();
//                    work.accept(helpUrl);
//                    if (helpUrl != null && helpUrl.trim().length() > 0) {
//                        String url = helpUrl + EXPRESSIONS_HELP_BASE_PATH;
//
//                        Window.open(url, "_blank", "");
//                    } else {
//                        AlertEvent.fireError(
//                                ExpressionPresenter.this,
//                                "Help is not configured!",
//                                null);
//                    }
//                })
//                .onFailure(caught -> AlertEvent.fireError(
//                        ExpressionPresenter.this,
//                        caught.getMessage(),
//                        null));
//    }

    protected String formatAnchor(String name) {
        return "#" + name.replace(" ", "-").toLowerCase();
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

        editorPresenter.registerCompletionProviders(
                functionsCompletionProvider,
                buildFieldsCompletionProvider());

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

//    private void buildCompletersForIndexFields() {
//        if (tablePresenter != null && tablePresenter.getIndexFieldsMap() != null) {
//            fieldCompletions = tablePresenter.getIndexFieldsMap()
//                    .keySet()
//                    .stream()
//                    .map(fieldName -> {
//                        return new AceCompletionValue(
//                                fieldName,
//                                "${" + fieldName + "}",
//                                "Field",
//                                DEFAULT_COMPLETION_SCORE);
//                    })
//                    .collect(Collectors.toList());
//
//        } else {
//            fieldCompletions = Collections.emptyList();
//        }
//    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String expression = editorPresenter.getText();
            if (EqualsUtil.isEquals(expression, field.getExpression())) {
                HidePopupEvent.fire(tablePresenter, this);
            } else {
                if (expression == null) {
                    fieldChangeConsumer.accept(field, field.copy().expression(null).build());
                    tablePresenter.setDirty(true);
                    tablePresenter.clearAndRefresh();
                    HidePopupEvent.fire(tablePresenter, this);
                } else {
                    // Check the validity of the expression.
                    final Rest<ValidateExpressionResult> rest = restFactory.create();
                    rest
                            .onSuccess(result -> {
                                if (result.isOk()) {
                                    fieldChangeConsumer.accept(field, field.copy().expression(expression).build());
                                    tablePresenter.setDirty(true);
                                    tablePresenter.clearAndRefresh();
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

    private BiFunction<FunctionDef, Ancestors, Command> createLeafBuilder() {
        return (functionDef, ancestors) -> {
            final String func = functionDef.toString();
            final String meta = ancestors.getAncestry(" ");

            functionCompletions.add(createAceCompletion(functionDef, meta));

//            createAceCompletion(func, func, meta)
//                    .ifPresent(functionCompletions::add);

            return () -> addFunction(func);
        };
    }

//    private LeafCommandBuilder createLeafBuilder(final String func) {
//        return (text, ancestors) -> {
//            final String meta = ancestors.getAncestry(" ");
//
//            createAceCompletion(text, func, meta)
//                    .ifPresent(functionCompletions::add);
//
//            return () -> addFunction(func);
//        };
//    }

    private List<Item> createFunctionsMenuItemsAndSnippets() {

        // TODO Once we have function definitions (name, desc, args) on the actual functions in
        //  stroom-expression we need to reinstate a variant of this to build the menu.
//        return new MenuBuilder()
//                .addBranch("Aggregate", childBuilder -> childBuilder
//                        .addLeaf(FunctionDef.AVERAGE, createLeafBuilder())
//                        .addLeaf(FunctionDef.COUNT, createLeafBuilder()))
//                .addBranch("Cast", childBuilder -> childBuilder
//                        .addLeaf(FunctionDef.TO_BOOLEAN, createLeafBuilder()))
//                .build();


        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createAggregateFunctions(item++, "Aggregate"));
        children.add(createCastFunctions(item++, "Cast"));
        children.add(createDateFunctions(item++, "Date"));
        children.add(createLinkFunctions(item++, "Link"));
        children.add(createLogicFunctions(item++, "Logic"));
        children.add(createMathematicsFunctions(item++, "Mathematics"));
        children.add(createParamFunctions(item++, "Param"));
        children.add(createRoundingFunctions(item++, "Rounding"));
        children.add(createSelectionFunctions(item++, "Selection"));
        children.add(createStringFunctions(item++, "String"));
        children.add(createTypeCheckingFunctions(item++, "Type Checking"));
        children.add(createUriFunctions(item++, "Uri"));
        children.add(createValueFunctions(item++, "Value"));
        return children;
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
                                    () -> addFunction(fieldName)))
                    .collect(Collectors.toList());
        } else {
            menuItems = Collections.emptyList();
        }

        return menuItems;
    }

    private Item createAggregateFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "average($)", "average("));
        children.add(createFunction(item++, "count()", "count()"));
        children.add(createFunction(item++, "countGroups()", "countGroups()"));
        children.add(createFunction(item++, "countUnique($)", "countUnique("));
        children.add(createFunction(item++, "joining($, delimiter, limit)", "joining("));
        children.add(createFunction(item++, "max($)", "max("));
        children.add(createFunction(item++, "min($)", "min("));
        children.add(createFunction(item++, "stDev($)", "stDev("));
        children.add(createFunction(item++, "sum($)", "sum("));
        children.add(createFunction(item++, "variance($)", "variance("));

        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createCastFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "toBoolean($)", "toBoolean("));
        children.add(createFunction(item++, "toDouble($)", "toDouble("));
        children.add(createFunction(item++, "toInteger($)", "toInteger("));
        children.add(createFunction(item++, "toLong($)", "toLong("));
        children.add(createFunction(item++, "toString($)", "toString("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createDateFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "formatDate($, pattern, timeZone)", "formatDate("));
        children.add(createFunction(item++, "parseDate($, pattern, timeZone)", "parseDate("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createLinkFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "annotation(text, annotationId, streamId, eventId, title, subject, status, assignedTo, comment)", "annotation("));
        children.add(createFunction(item++, "dashboard(text, uuid, params)", "dashboard("));
        children.add(createFunction(item++, "data(text, id, partNo, recordNo, lineFrom, colFrom, lineTo, colTo, [displayType, viewType])", "data("));
        children.add(createFunction(item++, "link(text, url, type)", "link("));
        children.add(createFunction(item++, "stepping(text, id, partNo, recordNo)", "stepping("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createLogicFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "equals($, $)", "equals("));
        children.add(createFunction(item++, "greaterThan($, $)", "greaterThan("));
        children.add(createFunction(item++, "greaterThanOrEqualTo($, $)", "greaterThanOrEqualTo("));
        children.add(createFunction(item++, "if($, then, else)", "if("));
        children.add(createFunction(item++, "lessThan($, $)", "lessThan("));
        children.add(createFunction(item++, "lessThanOrEqualTo($, $)", "lessThanOrEqualTo("));
        children.add(createFunction(item++, "not($)", "not("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createMathematicsFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "add($)", "add("));
        children.add(createFunction(item++, "average($)", "average("));
        children.add(createFunction(item++, "divide($)", "divide("));
        children.add(createFunction(item++, "max($)", "max("));
        children.add(createFunction(item++, "min($)", "min("));
        children.add(createFunction(item++, "modulus($)", "modulus("));
        children.add(createFunction(item++, "multiply($)", "multiply("));
        children.add(createFunction(item++, "negate($)", "negate("));
        children.add(createFunction(item++, "power($)", "power("));
        children.add(createFunction(item++, "random()", "random()"));
        children.add(createFunction(item++, "subtract($)", "subtract("));
        children.add(createFunction(item++, "sum($)", "sum("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createParamFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "currentUser()", "currentUser()"));
        children.add(createFunction(item++, "params()", "params()"));
        children.add(createFunction(item++, "param($)", "param("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createRoundingFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createCommonSubMenuItems(item++, "ceiling"));
        children.add(createCommonSubMenuItems(item++, "floor"));
        children.add(createCommonSubMenuItems(item++, "round"));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createCommonSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, func + "($, [n])", func + "("));
        children.add(createFunction(item++, func + "Year($)", func + "Year("));
        children.add(createFunction(item++, func + "Month($)", func + "Month("));
        children.add(createFunction(item++, func + "Day($)", func + "Day("));
        children.add(createFunction(item++, func + "Hour($)", func + "Hour("));
        children.add(createFunction(item++, func + "Minute($)", func + "Minute("));
        children.add(createFunction(item++, func + "Second($)", func + "Second("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createSelectionFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "any($)", "any("));
        children.add(createFunction(item++, "first($)", "first("));
        children.add(createFunction(item++, "last($)", "last("));
        children.add(createFunction(item++, "nth($, num)", "nth("));
        children.add(createFunction(item++, "top($, delimiter, rows)", "top("));
        children.add(createFunction(item++, "bottom($, delimiter, rows)", "bottom("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createStringFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "concat($, $)", "concat("));
        children.add(createFunction(item++, "decode($, [search, replace]..., otherwise)", "decode("));
        children.add(createFunction(item++, "decodeUrl($)", "decodeUrl("));
        children.add(createFunction(item++, "encodeUrl($)", "encodeUrl("));
        children.add(createFunction(item++, "exclude($, [regex...])", "exclude("));
        children.add(createFunction(item++, "hash($, algorithm)", "hash("));
        children.add(createFunction(item++, "include($, [regex...])", "include("));
        children.add(createFunction(item++, "indexOf($, string)", "indexOf("));
        children.add(createFunction(item++, "lastIndexOf($, string)", "lastIndexOf("));
        children.add(createFunction(item++, "lowerCase($)", "lowerCase("));
        children.add(createFunction(item++, "match($, regex)", "match("));
        children.add(createFunction(item++, "replace($, regex, replacement)", "replace("));
        children.add(createFunction(item++, "stringLength($)", "stringLength("));
        children.add(createFunction(item++, "substring($, startPos, endPos)", "substring("));
        children.add(createFunction(item++, "substringAfter($, string)", "substringAfter("));
        children.add(createFunction(item++, "substringBefore($, string)", "substringBefore("));
        children.add(createFunction(item++, "upperCase($)", "upperCase("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createTypeCheckingFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "isBoolean($)", "isBoolean("));
        children.add(createFunction(item++, "isDouble($)", "isDouble("));
        children.add(createFunction(item++, "isError($)", "isError("));
        children.add(createFunction(item++, "isInteger($)", "isInteger("));
        children.add(createFunction(item++, "isLong($)", "isLong("));
        children.add(createFunction(item++, "isNull($)", "isNull("));
        children.add(createFunction(item++, "isNumber($)", "isNumber("));
        children.add(createFunction(item++, "isString($)", "isString("));
        children.add(createFunction(item++, "isValue($)", "isValue("));
        children.add(createFunction(item++, "typeOf($)", "typeOf("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createUriFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "extractAuthorityFromUri($)", "extractAuthorityFromUri("));
        children.add(createFunction(item++, "extractFragmentFromUri($)", "extractFragmentFromUri("));
        children.add(createFunction(item++, "extractHostFromUri($)", "extractHostFromUri("));
        children.add(createFunction(item++, "extractPathFromUri($)", "extractPathFromUri("));
        children.add(createFunction(item++, "extractPortFromUri($)", "extractPortFromUri("));
        children.add(createFunction(item++, "extractQueryFromUri($)", "extractQueryFromUri("));
        children.add(createFunction(item++, "extractSchemeFromUri($)", "extractSchemeFromUri("));
        children.add(createFunction(item++, "extractSchemeSpecificPartFromUri($)", "extractSchemeSpecificPartFromUri("));
        children.add(createFunction(item++, "extractUserInfoFromUri($)", "extractUserInfoFromUri("));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createValueFunctions(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "err()", "err()"));
        children.add(createFunction(item++, "false()", "false()"));
        children.add(createFunction(item++, "null()", "null()"));
        children.add(createFunction(item++, "true()", "true()"));
        return new SimpleParentMenuItem(pos, func, children);
    }

    private Item createFunction(final int pos, final String text, final String func) {

        // TODO @AT This is a stop gap till we can get all the info needed to make the snippet
        //   defined in stroom-expression
        createAceCompletionSnippet(text, func, "")
                .ifPresent(functionCompletions::add);

        return new SimpleMenuItem(
                pos,
                text,
                null,
                true,
                () -> addFunction(func));
    }

    private Optional<AceCompletion> createAceCompletion(final String text,
                                                        final String func,
                                                        final String metaPrefix) {

        if (text.endsWith("()")) {
            return Optional.of(createAceCompletionValue(text, func, metaPrefix));
        } else if (func.endsWith(")")) {
            return createAceCompletionSnippet(text, func, metaPrefix);
        } else {
            GWT.log("Unknown func " + func);
            return Optional.empty();
        }
    }

    private AceCompletion createAceCompletion(final FunctionDef functionDef,
                                              final String metaPrefix) {
        final String name = functionDef.toString();
        final String snippetText = getSnippetFromFunction(functionDef);

        final String html = TooltipUtil.builder()
                .addHeading(name)
                .addSeparator()
                .addLine(functionDef.getDescription())
                .build()
                .asString();

        return new AceCompletionSnippet(
                name,
                snippetText,
                DEFAULT_COMPLETION_SCORE,
                metaPrefix + " Function",
                html);
    }

    private String getSnippetFromFunction(final FunctionDef functionDef) {
        // replace($, regex, replacement) ==> replace(${1}, ${2:regex}, ${3:replacement})$0

        final StringBuilder stringBuilder = new StringBuilder()
                .append(functionDef.getName())
                .append("(");

        for (int i = 0; i < functionDef.getArgs().size(); i++) {
            final String arg = functionDef.getArgs().get(i);
            final int snippetArgNo = i + 1;
            if (i != 0) {
                // put commas back in
                stringBuilder
                        .append(", ");
            }
            stringBuilder
                    .append("${")
                    .append(snippetArgNo);

            if (!arg.equals("$")) {
                stringBuilder
                        .append(":")
                        .append(arg);
            }
            stringBuilder
                    .append("}");
        }

        stringBuilder.append(")${0}"); // cursor pos after all args are tabbed through

        return stringBuilder.toString();
    }

    private AceCompletion createAceCompletionValue(final String text,
                                                   final String func,
                                                   final String metaPrefix) {
        return new AceCompletionValue(
                getSnippetNameFromText(text),
                func,
                metaPrefix + " Value",
                DEFAULT_COMPLETION_SCORE);
    }

    private Optional<AceCompletion> createAceCompletionSnippet(final String text,
                                                               final String func,
                                                               final String metaPrefix) {
        return getSnippetFromText(text)
                .map(snippetText -> {
                    final String name = getSnippetNameFromText(text);
                    final String html = SafeHtmlUtils.htmlEscape(text);
                    final String meta = metaPrefix
                            + (isValueFunction(name) ? "Value" : "Function");
//                    GWT.log(name + " - " + snippetText + " - " + meta);
                    return new AceCompletionSnippet(
                            name,
                            snippetText,
                            DEFAULT_COMPLETION_SCORE,
                            meta,
                            html);
                });
    }

    private boolean isValueFunction(final String name) {
        return name.endsWith("()");
    }

    private String getSnippetNameFromText(final String text) {
        // e.g.
        // replace($,regex,replacement) ==> replace($, $, $)
        // true() => true()
        if (text.endsWith("()")) {
            return text;
        } else {
            final String funcName = text.substring(0, text.indexOf("("));
            try {
                String argsStr = text.substring(text.indexOf("(") + 1);
                argsStr = argsStr.substring(0, argsStr.length() - 1);
                final String[] args = argsStr.split(",\\s?");
                final StringBuilder stringBuilder = new StringBuilder()
                        .append(funcName)
                        .append("(");

                for (int i = 0; i < args.length; i++) {
                    final String arg = args[i];
                    if (i != 0) {
                        // put commas back in
                        stringBuilder
                                .append(", ");
                    }
                    stringBuilder
                            .append("$");
                }
                stringBuilder.append(")"); // cursor pos after all args are tabbed through

//                GWT.log(text + " => " + stringBuilder.toString());
                return stringBuilder.toString();
            } catch (Exception e) {
                GWT.log("Error parsing " + text + " - " + e.getMessage());
                return funcName + "(...)";
            }
        }
    }

    private Optional<String> getSnippetFromText(final String text) {
        try {
            // replace($,regex,replacement) ==> replace(${1}, ${2:regex}, ${3:replacement})$0

            final StringBuilder stringBuilder = new StringBuilder();
            if (text.endsWith("()")) {
                // e.g. true() => true()${0}
                stringBuilder
                        .append(text)
                        .append("${0}");
            } else {
                final String funcName = text.substring(0, text.indexOf("("));
                String argsStr = text.substring(text.indexOf("(") + 1);
                argsStr = argsStr.substring(0, argsStr.length() - 1);
                // Remove any square brackets for optional args
                final String[] args = argsStr.replaceAll("[\\[\\]]", "")
                        .split(",\\s?");
                stringBuilder
                        .append(funcName)
                        .append("(");

                for (int i = 0; i < args.length; i++) {
                    final String arg = args[i];
                    final int snippetArgNo = i + 1;
                    if (i != 0) {
                        // put commas back in
                        stringBuilder
                                .append(", ");
                    }
                    stringBuilder
                            .append("${")
                            .append(snippetArgNo);

                    if (!arg.equals("$")) {
                        stringBuilder
                                .append(":")
                                .append(arg);
                    }
                    stringBuilder
                            .append("}");
                }
                stringBuilder.append(")${0}"); // cursor pos after all args are tabbed through
            }


            return Optional.of(stringBuilder.toString());
        } catch (Exception e) {
            GWT.log("Error parsing " + text + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    private void addFunction(final String func) {
        // will insert if there is no selection
        editorPresenter.replaceSelectedText(func);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface ExpressionView extends View, HasUiHandlers<ExpressionUiHandlers> {

        void setEditor(final EditorView editor);

        ButtonView addButton(final SvgPreset preset);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static enum FunctionDef {
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
