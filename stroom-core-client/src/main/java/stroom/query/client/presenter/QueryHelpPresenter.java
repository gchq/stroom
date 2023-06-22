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

import stroom.data.table.client.MyCellTable;
import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.client.presenter.QueryHelpItem.FunctionCategoryItem;
import stroom.query.client.presenter.QueryHelpItem.FunctionItem;
import stroom.query.client.presenter.QueryHelpItem.OverloadedFunctionHeadingItem;
import stroom.query.client.presenter.QueryHelpItem.QueryHelpItemHeading;
import stroom.query.client.presenter.QueryHelpItem.TopLevelHeadingItem;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.view.client.presenter.DataSourceFieldsMap;
import stroom.view.client.presenter.IndexLoader;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class QueryHelpPresenter
        extends MyPresenterWidget<QueryHelpView>
        implements QueryHelpUiHandlers {

    private static final int DEBOUNCE_PERIOD_MILLIS = 2000;

    private final SingleSelectionModel<QueryHelpItem> elementSelectionModel = new SingleSelectionModel<>();
    private final DoubleSelectTester doubleSelectTest = new DoubleSelectTester();
    private final CellTable<QueryHelpItem> elementChooser;
    private final FunctionSignatures functionSignatures;
    private final IndexLoader indexLoader;

    private final List<QueryHelpItem> queryHelpItems = new ArrayList<>();
    private final Set<QueryHelpItem> openItems = new HashSet<>();
    private QueryHelpItem lastSelection;

    private final QueryHelpItemHeading fieldsHeading;

    private Timer requestTimer;
    private String currentQuery;

    @Inject
    public QueryHelpPresenter(final EventBus eventBus,
                              final QueryHelpView view,
                              final Views views,
                              final FunctionSignatures functionSignatures,
                              final QueryStructure queryStructure,
                              final IndexLoader indexLoader) {
        super(eventBus, view);
        this.functionSignatures = functionSignatures;
        this.indexLoader = indexLoader;
        elementChooser = new MyCellTable<>(Integer.MAX_VALUE);
        view.setUiHandlers(this);

        final QueryHelpItem dataSourceHeading = new TopLevelHeadingItem(
                "Data Sources",
                0,
                SafeHtmlUtil.from("A list of data sources that can be queried by specifying them in " +
                        "the 'from' clause."));
        final QueryHelpItem structureHeading = new TopLevelHeadingItem(
                "Structure",
                0,
                SafeHtmlUtil.from("A list of the keywords available in the Stroom Query Language."));
        fieldsHeading = new TopLevelHeadingItem(
                "Fields",
                0,
                SafeHtmlUtil.from("A list of the fields available to 'select' from the specified " +
                        "data source. The fields will only become available one the data source has been " +
                        "specified using the 'from' keyword."));
        final QueryHelpItem functionsHeading = new TopLevelHeadingItem(
                "Functions",
                0,
                SafeHtmlUtil.from("A list of functions available to use the Stroom Query Language."));
        queryHelpItems.add(dataSourceHeading);
        queryHelpItems.add(fieldsHeading);
        queryHelpItems.add(structureHeading);
        queryHelpItems.add(functionsHeading);

        // Add structure.
        queryStructure.fetchStructureElements(list -> {
            list.forEach(structureHeading::addOrGetChild);
            refresh();
        });

        // Add views.
        views.fetchViews(viewDocRefs -> {
            viewDocRefs.stream()
                    .sorted()
                    .forEach(viewDocRef -> {

                        final String name = viewDocRef.getName();
                        final String uuid = viewDocRef.getUuid();
                        final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
                        appendKeyValueTable(htmlBuilder,
                                Arrays.asList(
                                        new SimpleEntry<>("Name:", name),
                                        new SimpleEntry<>("UUID:", uuid)));

                        dataSourceHeading.addOrGetChild(new DataSourceHelpItem(
                                name, htmlBuilder.toSafeHtml(), null));
                    });
            viewDocRefs.forEach(viewName -> {
                // Add function.
            });
            refresh();
        });

        // Add functions (ignoring the short form ones like + - * / as the help text can't cope with them
        functionSignatures.fetchHelpUrl(helpUrl ->
                functionSignatures.fetchFunctions(functions -> {
                    functions.stream()
                            .filter(FunctionSignatureUtil::isBracketedForm)
                            .forEach(functionSignature -> {
                                QueryHelpItem parent = functionsHeading;

                                // Add categories.
                                final List<String> categories = functionSignature.getCategoryPath();
                                for (final String category : categories) {
                                    final FunctionCategoryItem categoryHeading =
                                            new FunctionCategoryItem(category, parent.getDepth() + 1);
                                    parent = parent.addOrGetChild(categoryHeading);
                                }

                                final String title = functionSignature.isOverloaded()
                                        ? FunctionSignatureUtil.buildSignatureStr(functionSignature)
                                        .replace(functionSignature.getName(), "...")
                                        : functionSignature.getName();

                                if (functionSignature.isOverloaded()) {
                                    final OverloadedFunctionHeadingItem overloadedFuncNameHeading =
                                            new OverloadedFunctionHeadingItem(
                                                    functionSignature.getName(), parent.getDepth() + 1);
                                    parent = parent.addOrGetChild(overloadedFuncNameHeading);
                                }

                                // Add function.
                                parent.addOrGetChild(new FunctionItem(
                                        title,
                                        functionSignature,
                                        helpUrl,
                                        parent.getDepth() + 1));
                            });
                    refresh();
                }));

        Column<QueryHelpItem, QueryHelpItem> expanderColumn =
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
//
//        // Text.
//        final Column<QueryHelpItem, SafeHtml> textColumn = new Column<QueryHelpItem, SafeHtml>(new SafeHtmlCell()) {
//            @Override
//            public SafeHtml getValue(final QueryHelpItem item) {
//                return item.getLabel();
//            }
//        };
//        elementChooser.addColumn(textColumn);
        elementChooser.setSelectionModel(elementSelectionModel,
                new AbstractSelectionEventManager<QueryHelpItem>(elementChooser) {

                    @Override
                    protected void onSelect(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onSelect(e);
                        GWT.log("onSelect: "
                                + GwtNullSafe.get(e, CellPreviewEvent::getValue, QueryHelpItem::getTitle));
                    }

                    @Override
                    protected void onMoveRight(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveRight(e);
                        if (e.getValue().hasChildren()) {
                            openItems.add(e.getValue());
                            refresh();
                        }
                        updateDetails(e.getValue());
                    }

                    @Override
                    protected void onMoveLeft(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveLeft(e);
                        if (e.getValue().hasChildren()) {
                            openItems.remove(e.getValue());
                            refresh();
                        }
                        updateDetails(e.getValue());
                    }

                    @Override
                    protected void onMoveDown(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveDown(e);
                        final QueryHelpItem keyboardSelectedItem = elementChooser.getVisibleItem(
                                elementChooser.getKeyboardSelectedRow());
                        updateDetails(keyboardSelectedItem);
                    }

                    @Override
                    protected void onMoveUp(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveUp(e);
                        final QueryHelpItem keyboardSelectedItem = elementChooser.getVisibleItem(
                                elementChooser.getKeyboardSelectedRow());
                        updateDetails(keyboardSelectedItem);
                    }

                    @Override
                    protected void onExecute(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onExecute(e);
                        final QueryHelpItem value = e.getValue();
                        final boolean doubleSelect = doubleSelectTest.test(value);

                        lastSelection = value;

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
                });

        getView().setElementChooser(elementChooser);
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
        registerHandler(indexLoader.addChangeDataHandler(e -> {
            functionSignatures.fetchHelpUrl(helpUrl -> {
                fieldsHeading.clear();
                final DataSourceFieldsMap dataSourceFieldsMap = indexLoader.getDataSourceFieldsMap();

                GwtNullSafe.map(dataSourceFieldsMap)
                        .entrySet()
                        .stream()
                        .sorted(Entry.comparingByKey())
                        .forEach(entry -> {
                            final String fieldName = entry.getKey();
                            final AbstractField field = entry.getValue();
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

//                            htmlBuilder.div(tableBuilder::write, Attribute.className("functionSignatureTable"));
                            fieldsHeading.addOrGetChild(new FieldHelpItem(
                                    fieldName, htmlBuilder.toSafeHtml(), helpUrl));
                        });
                refresh();
            });
        }));
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
        htmlBuilder.div(tableBuilder::write, Attribute.className("functionSignatureTable"));
    }

    private String asDisplayValue(final boolean bool) {
        return bool
                ? "True"
                : "False";
    }

    private void refresh() {
        final List<QueryHelpItem> list = new ArrayList<>();
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
        if (queryHelpItem.hasChildren()) {
            for (final QueryHelpItem child : queryHelpItem.getChildren()) {
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
            InsertQueryElementEvent.fire(this, lastSelection.getInsertText());
        }
    }

    public void updateQuery(final String query) {
        // Debounce requests so we don't spam the backend
        if (requestTimer != null) {
            requestTimer.cancel();
        }

        requestTimer = new Timer() {
            @Override
            public void run() {
                setQuery(query);
            }
        };
        requestTimer.schedule(DEBOUNCE_PERIOD_MILLIS);
    }

    public void setQuery(final String query) {
        if (!Objects.equals(this.currentQuery, query)) {
            this.currentQuery = query;
            indexLoader.loadDataSource(currentQuery);
        }
    }

    public HandlerRegistration addInsertHandler(InsertQueryElementEvent.Handler handler) {
        return addHandlerToSource(InsertQueryElementEvent.getType(), handler);
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

        public DataSourceHelpItem(final String title, final SafeHtml detail, final String helpUrlBase) {
            super(title, false, 1);
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(title));
                hb1.br();
                hb1.hr();

                hb1.para(hb2 -> hb2.append(detail),
                        Attribute.className("functionSignatureInfo-description"));

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
            }, Attribute.className("functionSignatureInfo"));

            this.detail = htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public String getInsertText() {
            return "from \"" + title + "\"";
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

        public FieldHelpItem(final String title, final SafeHtml detail, final String helpUrlBase) {
            super(title, false, 1);
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(title));
                hb1.br();
                hb1.hr();

                hb1.para(hb2 -> hb2.append(detail),
                        Attribute.className("functionSignatureInfo-description"));

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
            }, Attribute.className("functionSignatureInfo"));

            this.detail = htmlBuilder.toSafeHtml();
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        public String getInsertText() {
            return "\"" + title + "\"";
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
}
