/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.query.client.presenter;

import stroom.dashboard.shared.FunctionSignature.OverloadType;
import stroom.data.table.client.MyCellTable;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.KeyedAceCompletionProvider;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.client.presenter.QueryHelpItem.FunctionCategoryItem;
import stroom.query.client.presenter.QueryHelpItem.FunctionItem;
import stroom.query.client.presenter.QueryHelpItem.OverloadedFunctionHeadingItem;
import stroom.query.client.presenter.QueryHelpItem.QueryHelpItemHeading;
import stroom.query.client.presenter.QueryHelpItem.TopLevelHeadingItem;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.ui.config.shared.Themes.ThemeType;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionValue;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QueryHelpPresenter
        extends MyPresenterWidget<QueryHelpView>
        implements QueryHelpUiHandlers {

    private static final int DEBOUNCE_PERIOD_MILLIS = 1_000;
    private static final String DATA_SOURCES_HELP_TEXT = "A list of data sources that can be queried by " +
            "specifying them in the 'from' clause.";
    private static final String STRUCTURE_HELP_TEXT = "A list of the keywords available in the Stroom Query Language.";
    private static final String FIELDS_HELP_TEXT = "A list of the fields available to 'select' from the specified " +
            "data source. The fields will only become available one the data source has been " +
            "specified using the 'from' keyword.";
    private static final String FUNCTIONS_HELP_TEXT = "A list of functions available to use in the Stroom " +
            "Query Language.";

    private static final String DATA_SOURCES_COMPLETION_KEY = "DATA_SOURCES";
    private static final String STRUCTURE_COMPLETION_KEY = "STRUCTURE";
    private static final String FIELDS_COMPLETION_KEY = "FIELDS";
    private static final String FUNCTIONS_COMPLETION_KEY = "FUNCTIONS";

    private static final String DATA_SOURCES_META = "Data Source";
    private static final String STRUCTURE_META = "Structure";
    private static final String FIELDS_META = "Field";
    private static final String FUNCTIONS_META = "Function";

    // These control the priority of the items in the code completion menu.
    // Higher value appear further up for the same matching chars.
    private static final int DATA_SOURCES_COMPLETION_SCORE = 500;
    private static final int STRUCTURE_COMPLETION_SCORE = 400;
    private static final int FIELDS_COMPLETION_SCORE = 300;
    private static final int FUNCTIONS_COMPLETION_SCORE = 200;

    private final SingleSelectionModel<QueryHelpItem> elementSelectionModel = new SingleSelectionModel<>();
    private final DoubleSelectTester doubleSelectTest = new DoubleSelectTester();
    private final CellTable<QueryHelpItem> elementChooser;
    private final FunctionSignatures functionSignatures;
    private final MarkdownConverter markdownConverter;
    private final QueryStructure queryStructure;
    private final KeyedAceCompletionProvider keyedAceCompletionProvider;
    private QueryHelpDataSupplier queryHelpDataSupplier;

    private final List<QueryHelpItem> queryHelpItems = new ArrayList<>();
    private final Set<QueryHelpItem> openItems = new HashSet<>();
    private QueryHelpItem lastSelection;
    private List<DocRef> lastFetchedViews = null;
    private DataSourceFieldsMap lastFetchedDataSourceFieldsMap = null;

    private QueryHelpItem dataSourceHeading;
    private QueryHelpItemHeading fieldsHeading;
    private QueryHelpItemHeading structureHeading;
    private QueryHelpItem functionsHeading;

    private Timer requestTimer;
    private String currentQuery;
    private ThemeType themeType = null;
    private Function<String, String> fieldDecorator;

    @Inject
    public QueryHelpPresenter(final EventBus eventBus,
                              final QueryHelpView view,
                              final FunctionSignatures functionSignatures,
                              final QueryStructure queryStructure,
                              final MarkdownConverter markdownConverter,
                              final KeyedAceCompletionProvider keyedAceCompletionProvider) {
        super(eventBus, view);
        this.functionSignatures = functionSignatures;
        this.markdownConverter = markdownConverter;
        this.queryStructure = queryStructure;
        this.keyedAceCompletionProvider = keyedAceCompletionProvider;
        elementChooser = new MyCellTable<>(Integer.MAX_VALUE);
        view.setUiHandlers(this);

        final Column<QueryHelpItem, QueryHelpItem> expanderColumn =
                new Column<QueryHelpItem, QueryHelpItem>(new QueryHelpItemCell(openItems)) {
                    @Override
                    public QueryHelpItem getValue(final QueryHelpItem object) {
                        return object;
                    }
                };
        expanderColumn.setFieldUpdater((index, row, value) -> {
            toggleOpen(row);
            refresh();
        });
        elementChooser.addColumn(expanderColumn);
        elementChooser.setSelectionModel(elementSelectionModel, buildSelectionEventManager());
        getView().setElementChooser(elementChooser);

        // Markdown styling has the light/dark theme baked in so have to rebuild it on theme change
        registerHandler(eventBus.addHandler(ChangeThemeEvent.getType(), event -> {
            // Rebuild the structure menu items as they contain markdown
            buildStructureMenuItems(() -> {
                elementSelectionModel.clear();
                getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
            });
        }));
    }

    private void initMenu(final FunctionSignatures functionSignatures,
                          final QueryHelpDataSupplier queryHelpDataSupplier) {
        if (queryHelpDataSupplier.isSupported(HelpItemType.DATA_SOURCE)) {
            dataSourceHeading = createTopLevelHelpItem(
                    queryHelpItems,
                    "Data Sources",
                    DATA_SOURCES_HELP_TEXT,
                    false);
            buildDataSourcesMenuItems();
        }
        if (queryHelpDataSupplier.isSupported(HelpItemType.STRUCTURE)) {
            structureHeading = createTopLevelHelpItem(
                    queryHelpItems,
                    "Structure",
                    STRUCTURE_HELP_TEXT,
                    false);
            buildStructureMenuItems();
        }
        if (queryHelpDataSupplier.isSupported(HelpItemType.FIELD)) {
            fieldsHeading = createTopLevelHelpItem(
                    queryHelpItems,
                    "Fields",
                    FIELDS_HELP_TEXT,
                    false);
            buildFieldsMenuItems();
        }
        if (queryHelpDataSupplier.isSupported(HelpItemType.FUNCTION)) {
            functionsHeading = createTopLevelHelpItem(
                    queryHelpItems,
                    "Functions",
                    FUNCTIONS_HELP_TEXT,
                    false);
            buildFunctionsMenuItems(functionSignatures);
        }
    }

    private void buildFunctionsMenuItems(final FunctionSignatures functionSignatures) {
        // Add functions (ignoring the short form ones like + - * / as the help text can't cope with them
        functionSignatures.fetchHelpUrl(helpUrl ->
                functionSignatures.fetchFunctions(functions -> {
                    keyedAceCompletionProvider.clear(FUNCTIONS_COMPLETION_KEY);
                    functions.stream()
                            .filter(FunctionSignatureUtil::isBracketedForm)
                            .forEach(functionSignature -> {
                                // Initial parent is the top level heading
                                QueryHelpItem parent = functionsHeading;

                                // Add the category path to the function. Functions can be multiple levels deep.
                                final List<String> categories = functionSignature.getCategoryPath();
                                for (final String category : categories) {
                                    // We need to get the correct child instance of this parent, which may not be
                                    // the one just created, as it may have been created for a previous funtion.
                                    parent = parent.addOrGetChild(new FunctionCategoryItem(parent, category));
                                }

                                final boolean isOverloadedInCategory = OverloadType.OVERLOADED_IN_CATEGORY.equals(
                                        functionSignature.getOverloadType());
                                if (isOverloadedInCategory) {
                                    // If a function has overloads then we need to create an extra category for
                                    // the overloads
                                    parent = parent.addOrGetChild(new OverloadedFunctionHeadingItem(
                                            parent,
                                            functionSignature.getName()));
                                }

                                // Create and register the completion snippet for this function
                                final AceCompletion aceCompletion =
                                        FunctionSignatureUtil.convertFunctionDefinitionToCompletion(
                                                functionSignature, helpUrl, FUNCTIONS_COMPLETION_SCORE);
                                keyedAceCompletionProvider.addCompletion(
                                        FUNCTIONS_COMPLETION_KEY,
                                        aceCompletion);

                                final String title = isOverloadedInCategory
                                        ? FunctionSignatureUtil.buildSignatureStr(functionSignature)
                                        .replaceAll("^" + functionSignature.getName(), "...")
                                        : functionSignature.getName();
                                // Add the actual function.
                                parent.addOrGetChild(new FunctionItem(
                                        parent,
                                        title,
                                        functionSignature,
                                        helpUrl));
                            });
                    refresh();
                }));
    }

    private void buildDataSourcesMenuItems() {
        // Add views.
        if (GwtNullSafe.test(queryHelpDataSupplier, supplier -> supplier.isSupported(HelpItemType.DATA_SOURCE))) {
            queryHelpDataSupplier.fetchDataSources(viewDocRefs -> {
                // Only need to re-build this part of the menu if the list of data sources has actually changed
                if (!Objects.equals(lastFetchedViews, viewDocRefs) || hasThemeChanged()) {
                    lastFetchedViews = viewDocRefs;
                    dataSourceHeading.clear();
                    if (!viewDocRefs.isEmpty()) {
                        viewDocRefs.stream()
                                .sorted()
                                .forEach(viewDocRef -> {
                                    keyedAceCompletionProvider.clear(DATA_SOURCES_COMPLETION_KEY);
                                    final String name = viewDocRef.getName();
                                    final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
                                    appendKeyValueTable(htmlBuilder,
                                            Arrays.asList(
                                                    new SimpleEntry<>("Name:", name),
                                                    new SimpleEntry<>("Type:", viewDocRef.getType()),
                                                    new SimpleEntry<>("UUID:", viewDocRef.getUuid())));

                                    // ctor add the item to its parent
                                    final DataSourceHelpItem dataSourceHelpItem = new DataSourceHelpItem(
                                            dataSourceHeading,
                                            name,
                                            htmlBuilder.toSafeHtml(),
                                            null);

                                    keyedAceCompletionProvider.addCompletion(
                                            DATA_SOURCES_COMPLETION_KEY,
                                            new AceCompletionValue(
                                                    dataSourceHelpItem.title,
                                                    dataSourceHelpItem.getInsertText(),
                                                    DATA_SOURCES_META,
                                                    dataSourceHelpItem.getDetail().asString(),
                                                    DATA_SOURCES_COMPLETION_SCORE));
                                });
                    } else {
                        createEmptyDataSourcesMenuItem(dataSourceHeading);
                    }
                    refresh();
                }
            });
        }
    }

    public AceCompletionProvider getKeyedAceCompletionProvider() {
        return keyedAceCompletionProvider;
    }

    public void setQueryHelpDataSupplier(final QueryHelpDataSupplier queryHelpDataSupplier) {
        this.queryHelpDataSupplier = queryHelpDataSupplier;
        if (queryHelpDataSupplier != null) {
            initMenu(functionSignatures, queryHelpDataSupplier);
            queryHelpDataSupplier.registerChangeHandler(dataSourceFieldsMap -> {
                buildFieldsMenuItems();
            });
        }
    }

    /**
     * This is needed as the theme is backed into the html detail of the menu items, so if
     * the theme changes we need to rebuild them.
     *
     * @return True if the theme has changed since last refresh.
     */
    private boolean hasThemeChanged() {
        final ThemeType themeType = markdownConverter.geCurrentThemeType();
        return !Objects.equals(themeType, this.themeType);
    }

    private TopLevelHeadingItem createTopLevelHelpItem(final List<QueryHelpItem> queryHelpItems,
                                                       final String title,
                                                       final String helpText,
                                                       final boolean isMarkdown) {
        final SafeHtml helpHtml;
        if (GwtNullSafe.isBlankString(helpText)) {
            helpHtml = SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else if (isMarkdown) {
            helpHtml = markdownConverter.convertMarkdownToHtml(helpText);
        } else {
            helpHtml = SafeHtmlUtil.from(helpText);
        }
        final TopLevelHeadingItem topLevelHeadingItem = new TopLevelHeadingItem(title, helpHtml);
        queryHelpItems.add(topLevelHeadingItem);
        return topLevelHeadingItem;
    }

    private void buildStructureMenuItems() {
        buildStructureMenuItems(null);
    }

    private void buildStructureMenuItems(final Runnable afterBuildAction) {
        if (GwtNullSafe.test(queryHelpDataSupplier, supplier -> supplier.isSupported(HelpItemType.STRUCTURE))) {
            structureHeading.clear();
            // Add structure.
            queryStructure.fetchStructureElements(structureHeading, list -> {
                keyedAceCompletionProvider.clear(STRUCTURE_COMPLETION_KEY);
                list.forEach(structureQueryHelpItem -> {
                    structureQueryHelpItem.getSnippets()
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(str -> !GwtNullSafe.isBlankString(str))
                            .forEach(snippet -> {
                                final AceCompletion aceCompletion = new AceCompletionSnippet(
                                        structureQueryHelpItem.title,
                                        snippet,
                                        STRUCTURE_COMPLETION_SCORE,
                                        STRUCTURE_META,
                                        structureQueryHelpItem.getDetail().asString());
                                keyedAceCompletionProvider.addCompletion(STRUCTURE_COMPLETION_KEY, aceCompletion);
                            });
                });
                refresh();
                GwtNullSafe.run(afterBuildAction);
            });
        }
    }

    private CellPreviewEvent.Handler<QueryHelpItem> buildSelectionEventManager() {

        return new AbstractSelectionEventManager<QueryHelpItem>(elementChooser) {

            @Override
            protected void onSelect(final CellPreviewEvent<QueryHelpItem> e) {
                super.onSelect(e);
                lastSelection = e.getValue();
//                GWT.log("onSelect: "
//                        + GwtNullSafe.get(e, CellPreviewEvent::getValue, QueryHelpItem::getTitle));
            }

            @Override
            protected void onMouseDown(final CellPreviewEvent<QueryHelpItem> e) {
                super.onMouseDown(e);
                lastSelection = e.getValue();
                updateMenu(e.getValue());
                updateDetails(e.getValue());
//                GWT.log("onMouseDown: "
//                        + GwtNullSafe.get(e, CellPreviewEvent::getValue, QueryHelpItem::getTitle));
            }

            @Override
            protected void onMoveRight(final CellPreviewEvent<QueryHelpItem> e) {
                super.onMoveRight(e);
                if (e.getValue().hasChildren()) {
                    openItems.add(e.getValue());
                    refresh();
                }
                updateMenu(e.getValue());
                updateDetails(e.getValue());
            }

            @Override
            protected void onMoveLeft(final CellPreviewEvent<QueryHelpItem> e) {
                super.onMoveLeft(e);
                if (e.getValue().hasChildren()) {
                    openItems.remove(e.getValue());
                    refresh();
                }
                updateMenu(e.getValue());
                updateDetails(e.getValue());
            }

            @Override
            protected void onMoveDown(final CellPreviewEvent<QueryHelpItem> e) {
                super.onMoveDown(e);
                final QueryHelpItem keyboardSelectedItem = elementChooser.getVisibleItem(
                        elementChooser.getKeyboardSelectedRow());
                lastSelection = keyboardSelectedItem;
                updateMenu(keyboardSelectedItem);
                updateDetails(keyboardSelectedItem);
            }

            @Override
            protected void onMoveUp(final CellPreviewEvent<QueryHelpItem> e) {
                super.onMoveUp(e);
                final QueryHelpItem keyboardSelectedItem = elementChooser.getVisibleItem(
                        elementChooser.getKeyboardSelectedRow());
                lastSelection = keyboardSelectedItem;
                updateMenu(keyboardSelectedItem);
                updateDetails(keyboardSelectedItem);
            }

            @Override
            protected void onExecute(final CellPreviewEvent<QueryHelpItem> e) {
                super.onExecute(e);
                final QueryHelpItem value = e.getValue();
                final boolean doubleSelect = doubleSelectTest.test(value);

                lastSelection = value;
                updateMenu(value);
                updateDetails(value);

                if (value != null) {
                    if (value.hasChildren()) {
                        toggleOpen(value);
                        refresh();
                    } else if (doubleSelect) {
                        onInsert();
                    }
                }

                getView().enableButtons(GwtNullSafe.test(
                        e.getValue(),
                        QueryHelpItem::getInsertType,
                        InsertType::isInsertable));
            }
        };
    }

    private void updateMenu(final QueryHelpItem queryHelpItem) {
        // The data sources may be changed externally so keep their list up to date
        buildDataSourcesMenuItems();
        // Fields may also have changed, due to data source removal, or if fields have been changed on a DS.
        buildFieldsMenuItems();
    }

    private void updateDetails(final QueryHelpItem queryHelpItem) {
//        final QueryHelpItem selectedItem = elementSelectionModel.getSelectedObject();
        final QueryHelpItem selectedItem = queryHelpItem;
        if (selectedItem == null) {
            getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            getView().setDetails(GwtNullSafe.requireNonNullElse(
                    selectedItem.getDetail(),
                    SafeHtmlUtils.EMPTY_SAFE_HTML));
        }
    }

    private void toggleOpen(final QueryHelpItem queryHelpItem) {
        if (openItems.contains(queryHelpItem)) {
            openItems.remove(queryHelpItem);
        } else {
            openItems.add(queryHelpItem);
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(elementSelectionModel.addSelectionChangeHandler(e -> {
//            final QueryHelpItem selected = elementSelectionModel.getSelectedObject();
//            if (selected != null) {
//                if (openItems.contains(selected)) {
//                    openItems.remove(selected);
//                } else {
//                    openItems.add(selected);
//                }
//            }
//            refresh();
        }));

//        registerHandler(indexLoader.addChangeDataHandler(e -> {
//            buildFieldsMenuItems();
//        }));
    }

    private void buildFieldsMenuItems() {
        if (GwtNullSafe.test(queryHelpDataSupplier, supplier -> supplier.isSupported(HelpItemType.FIELD))) {
            functionSignatures.fetchHelpUrl(helpUrl -> {
                final DataSourceFieldsMap dataSourceFieldsMap = queryHelpDataSupplier.getDataSourceFieldsMap();

                if (!Objects.equals(lastFetchedDataSourceFieldsMap, dataSourceFieldsMap) || hasThemeChanged()) {
                    lastFetchedDataSourceFieldsMap = dataSourceFieldsMap;
                    fieldsHeading.clear();
                    keyedAceCompletionProvider.clear(FIELDS_COMPLETION_KEY);

                    if (GwtNullSafe.hasEntries(dataSourceFieldsMap)) {
//                        GWT.log("Adding " + dataSourceFieldsMap.size() + " fields");
                        dataSourceFieldsMap
                                .entrySet()
                                .stream()
                                .sorted(Entry.comparingByKey())
                                .forEach(entry -> {
                                    final String fieldName = entry.getKey();
                                    final AbstractField field = entry.getValue();
                                    addFieldToMenu(helpUrl, fieldName, field);
                                });
                    } else {
                        createEmptyFieldsMenuItem(fieldsHeading);
                    }
                    refresh();
                }
            });
        }
    }

    private QueryHelpItem createEmptyDataSourcesMenuItem(final QueryHelpItem parent) {
        // ctor add the item to its parent
        return new QueryHelpItem(parent, "[Empty]", false) {
            @Override
            public InsertType getInsertType() {
                return InsertType.NOT_INSERTABLE;
            }

            @Override
            public SafeHtml getDetail() {
                return markdownConverter.convertMarkdownToHtml(
                        "There are no _Views_ to query. Create a _View_ in the explorer tree first.");
            }
        };
    }

    private QueryHelpItem createEmptyFieldsMenuItem(final QueryHelpItem parent) {
        // ctor add the item to its parent
        return new QueryHelpItem(parent, "[Empty]", false) {
            @Override
            public InsertType getInsertType() {
                return InsertType.NOT_INSERTABLE;
            }

            @Override
            public SafeHtml getDetail() {
                //noinspection TextBlockMigration
                return markdownConverter.convertMarkdownToHtml(
                        "The list of _Fields_ are not known until the _Data Source_ " +
                                "can be determined from the query." +
                                "\n\nSet the _Data Source_ using:" +
                                "\n```" +
                                "\nfrom x" +
                                "\n```" +
                                "\nwhere `x` is one of the _Data Sources_ in the list above.");
            }
        };
    }

    private void addFieldToMenu(final String helpUrl, final String fieldName, final AbstractField field) {
        final String fieldType = field.getFieldType().getDisplayValue();
        final String supportedConditions = field.getConditions()
                .stream()
                .map(Condition::getDisplayValue)
                .map(str -> "'" + str + "'")
                .collect(Collectors.joining(", "));

        final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
        appendKeyValueTable(htmlBuilder,
                Arrays.asList(
                        new SimpleEntry<>("Name:", fieldName),
                        new SimpleEntry<>("Type:", fieldType),
                        new SimpleEntry<>("Supported Conditions:", supportedConditions),
                        new SimpleEntry<>("Is queryable:", asDisplayValue(field.queryable())),
                        new SimpleEntry<>("Is numeric:", asDisplayValue(field.isNumeric()))));

        // ctor adds item to its parent
        final FieldHelpItem fieldHelpItem = new FieldHelpItem(
                fieldsHeading,
                fieldName,
                queryHelpDataSupplier::decorateFieldName,
                htmlBuilder.toSafeHtml(),
                helpUrl);

        keyedAceCompletionProvider.addCompletion(
                FIELDS_COMPLETION_KEY,
                new AceCompletionValue(
                        fieldName,
                        fieldHelpItem.getInsertText(),
                        FIELDS_META,
                        fieldHelpItem.getDetail().asString(),
                        FIELDS_COMPLETION_SCORE));
    }

    private void appendKeyValueTable(final HtmlBuilder htmlBuilder,
                                     final List<Entry<String, String>> entries) {

        final TableBuilder tableBuilder = new TableBuilder();
        for (final Entry<String, String> entry : entries) {
            tableBuilder.row(
                    HtmlBuilder.builder()
                            .bold(htmlBuilder2 -> htmlBuilder2.append(entry.getKey()))
                            .toSafeHtml(),
                    SafeHtmlUtil.from(entry.getValue()));
        }
        htmlBuilder.div(tableBuilder::write, Attribute.className("queryHelpDetail-table"));
    }

    private String asDisplayValue(final boolean bool) {
        return bool
                ? "True"
                : "False";
    }

    private void refresh() {
        this.themeType = markdownConverter.geCurrentThemeType();

        final List<QueryHelpItem> list = new ArrayList<>();
//        GWT.log("openItems:\n" + GwtNullSafe.stream(openItems)
//                .map(QueryHelpItem::getTitle)
//                .collect(Collectors.joining("\n")));

        for (final QueryHelpItem queryHelpItem : queryHelpItems) {
            list.add(queryHelpItem);
            if (openItems.contains(queryHelpItem)) {
                addChildren(queryHelpItem, list);
            }
        }
        elementChooser.setRowData(list);
        elementChooser.setRowCount(list.size());
    }

    private void addChildren(final QueryHelpItem queryHelpItem, final List<QueryHelpItem> list) {
//        GWT.log("addChildren, queryHelpItem: " + queryHelpItem.title);
        if (queryHelpItem.hasChildren()) {
            for (final QueryHelpItem child : queryHelpItem.getChildren()) {
//                GWT.log("addChildren, queryHelpItem: " + queryHelpItem.title + ", child: " + child.title);
                list.add(child);
                if (openItems.contains(child)) {
                    addChildren(child, list);
                }
            }
        }
    }

    @Override
    public void onCopy() {
        if (lastSelection != null) {
            ClipboardUtil.copy(lastSelection.getInsertText());
        }
    }

    @Override
    public void onInsert() {
        if (lastSelection != null) {
            InsertEditorTextEvent.fire(
                    this,
                    lastSelection.getInsertText(),
                    lastSelection.getInsertType());
        }
    }

    public void updateQuery(final String query, final Consumer<String> onQueryChange) {
        // Debounce requests so we don't spam the backend
        if (requestTimer != null) {
            requestTimer.cancel();
        }

        requestTimer = new Timer() {
            @Override
            public void run() {
                setQuery(query, onQueryChange);
            }
        };
        requestTimer.schedule(DEBOUNCE_PERIOD_MILLIS);
    }

    public void setQuery(final String query, final Consumer<String> onQueryChange) {
        if (!Objects.equals(this.currentQuery, query)) {
            this.currentQuery = query;
            if (onQueryChange != null) {
                onQueryChange.accept(currentQuery);
            }
        }
    }

    private HandlerRegistration addInsertHandler(InsertEditorTextEvent.Handler handler) {
        return addHandlerToSource(InsertEditorTextEvent.getType(), handler);
    }

    /**
     * Associate this {@link QueryHelpPresenter} with an editor. The editor's will be set to use
     * this {@link QueryHelpPresenter} as its completion provider and items inserted using the
     * query help menu will be inserted into the editor.
     */
    public void linkToEditor(final EditorPresenter editorPresenter) {
        Objects.requireNonNull(editorPresenter);

        // Set up the insert handler
        registerHandler(addInsertHandler(insertEditorTextEvent -> {
            if (InsertType.SNIPPET.equals(insertEditorTextEvent.getInsertType())) {
                editorPresenter.insertSnippet(insertEditorTextEvent.getText());
                editorPresenter.focus();
            } else if (insertEditorTextEvent.getInsertType().isInsertable()) {
                editorPresenter.insertTextAtCursor(insertEditorTextEvent.getText());
                editorPresenter.focus();
            }
        }));

        // This glues the editor code completion to the QueryHelpPresenter's completion provider
        // Need to do this via addAttachHandler so the editor is fully loaded
        // else it moans about the id not being a thing on the AceEditor
        editorPresenter.getWidget().addAttachHandler(event -> {
            editorPresenter.registerCompletionProviders(getKeyedAceCompletionProvider());
        });
    }


    // --------------------------------------------------------------------------------


    public interface QueryHelpView extends View, HasUiHandlers<QueryHelpUiHandlers> {

        void setElementChooser(Widget widget);

        void setDetails(SafeHtml details);

        void enableButtons(boolean enable);
    }


    // --------------------------------------------------------------------------------


    public static class DataSourceHelpItem extends QueryHelpItem {

        private final SafeHtml detail;

        public DataSourceHelpItem(final QueryHelpItem parent,
                                  final String title,
                                  final SafeHtml detail,
                                  final String helpUrlBase) {
            super(parent, title, false);

            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(htmlBuilder2 -> {
                htmlBuilder2.bold(htmlBuilder3 -> htmlBuilder3.append(title));
                htmlBuilder2.br();
                htmlBuilder2.hr();

                htmlBuilder2.para(htmlBuilder3 -> htmlBuilder3.append(detail),
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

//                if (helpUrlBase != null) {
//                    hb1.append("For more information see the ");
//                    hb1.appendLink(
//                            helpUrlBase +
//                                    "/user-guide/stroom-query-language/structure/" +
//                                    title.toLowerCase().replace(" ", "-") +
//                                    "#" +
//                                    title,
//                            "Help Documentation");
//                    hb1.append(".");
//                }
            }, Attribute.className("queryHelpDetail"));

            this.detail = htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public String getInsertText() {
            return GwtNullSafe.get(title, title2 ->
                    title2.contains(" ")
                            ? "from \"" + title2 + "\""
                            : "from " + title2);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " queryHelpItem-leaf";
        }

        @Override
        public InsertType getInsertType() {
            return GwtNullSafe.isBlankString(title)
                    ? InsertType.BLANK
                    : InsertType.PLAIN_TEXT;
        }
    }


    // --------------------------------------------------------------------------------


    public static class FieldHelpItem extends QueryHelpItem {

        private final SafeHtml detail;
        private final Function<String, String> insertTextDecorator;

        public FieldHelpItem(final QueryHelpItem parent,
                             final String title,
                             final Function<String, String> insertTextDecorator,
                             final SafeHtml detail,
                             final String helpUrlBase) {
            super(parent, title, false);
            this.insertTextDecorator = insertTextDecorator;
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

//                if (helpUrlBase != null) {
//                    hb1.append("For more information see the ");
//                    hb1.appendLink(
//                            helpUrlBase +
//                                    "/user-guide/stroom-query-language/structure/" +
//                                    title.toLowerCase().replace(" ", "-") +
//                                    "#" +
//                                    title,
//                            "Help Documentation");
//                    hb1.append(".");
//                }
            }, Attribute.className("queryHelpDetail"));

            this.detail = htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public String getInsertText() {
            return GwtNullSafe.get(title, title2 -> insertTextDecorator != null
                    ? insertTextDecorator.apply(title2)
                    : title2);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " queryHelpItem-leaf";
        }

        @Override
        public InsertType getInsertType() {
            return GwtNullSafe.isBlankString(title)
                    ? InsertType.BLANK
                    : InsertType.PLAIN_TEXT;
        }
    }


    // --------------------------------------------------------------------------------


    public enum InsertType {

        PLAIN_TEXT(true),
        SNIPPET(true),
        BLANK(false),
        NOT_INSERTABLE(false);

        private final boolean isInsertable;

        InsertType(final boolean isInsertable) {
            this.isInsertable = isInsertable;
        }

        public boolean isInsertable() {
            return isInsertable;
        }
    }


    // --------------------------------------------------------------------------------


    public interface QueryHelpDataSupplier {

        DataSourceFieldsMap getDataSourceFieldsMap();

        /**
         * Decorates a field name for insertion into the editor
         */
        String decorateFieldName(final String fieldName);

        /**
         * Register a handler to be called when the data used by the {@link QueryHelpPresenter} changes.
         */
        void registerChangeHandler(final Consumer<DataSourceFieldsMap> onChange);

        boolean isSupported(final HelpItemType helpItemType);

        void fetchDataSources(final Consumer<List<DocRef>> dataSourceConsumer);
    }


    // --------------------------------------------------------------------------------


    public enum HelpItemType {
        DATA_SOURCE,
        STRUCTURE,
        FIELD,
        FUNCTION
    }
}
