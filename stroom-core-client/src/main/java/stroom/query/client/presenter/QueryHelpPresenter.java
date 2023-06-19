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
import stroom.query.client.presenter.QueryHelpItem.FunctionCategoryItem;
import stroom.query.client.presenter.QueryHelpItem.FunctionItem;
import stroom.query.client.presenter.QueryHelpItem.QueryHelpItemHeading;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.util.client.ClipboardUtil;
import stroom.view.client.presenter.IndexLoader;
import stroom.widget.util.client.AbstractSelectionEventManager;
import stroom.widget.util.client.DoubleSelectTester;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

        final QueryHelpItemHeading dataSourceHeading = new QueryHelpItemHeading("Data Sources", 0);
        final QueryHelpItemHeading structureHeading = new QueryHelpItemHeading("Structure", 0);
        fieldsHeading = new QueryHelpItemHeading("Fields", 0);
        final QueryHelpItemHeading functionsHeading = new QueryHelpItemHeading("Functions", 0);
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
        views.fetchViews(viewNames -> {
            viewNames.forEach(viewName -> {
                // Add function.
                dataSourceHeading.addOrGetChild(new DataSourceHelpItem(viewName, viewName, null));
            });
            refresh();
        });

        // Add functions.
        functionSignatures.fetchHelpUrl(helpUrl -> functionSignatures.fetchFunctions(functions -> {
            functions.forEach(functionSignature -> {
                QueryHelpItem parent = functionsHeading;

                // Add categories.
                final List<String> categories = functionSignature.getCategoryPath();
                for (final String category : categories) {
                    final FunctionCategoryItem categoryHeading =
                            new FunctionCategoryItem(category, parent.getDepth() + 1);
                    parent = parent.addOrGetChild(categoryHeading);
                }

                // Add function.
                parent.addOrGetChild(new FunctionItem(functionSignature, helpUrl, parent.getDepth() + 1));
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
                    protected void onMoveRight(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveRight(e);
                        if (e.getValue().hasChildren()) {
                            openItems.add(e.getValue());
                            refresh();
                        }
                    }

                    @Override
                    protected void onMoveLeft(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onMoveLeft(e);
                        if (e.getValue().hasChildren()) {
                            openItems.remove(e.getValue());
                            refresh();
                        }
                    }

                    @Override
                    protected void onExecute(final CellPreviewEvent<QueryHelpItem> e) {
                        super.onExecute(e);
                        final QueryHelpItem value = e.getValue();
                        final boolean doubleSelect = doubleSelectTest.test(value);

                        lastSelection = value;

                        if (value == null) {
                            getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);

                        } else {
                            getView().setDetails(value.getDetail());
                            if (value.hasChildren()) {
                                toggleOpen(value);
                                refresh();
                            } else if (doubleSelect) {
                                onInsert();
                            }
                        }

                        getView().enableButtons(e.getValue() != null &&
                                !e.getValue().getDetail().asString().equals(SafeHtmlUtils.EMPTY_SAFE_HTML.asString()));
                    }
                });

        getView().setElementChooser(elementChooser);
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
                final List<String> fieldNames = indexLoader.getIndexFieldNames();
                if (fieldNames != null) {
                    fieldNames.forEach(fieldName ->
                            fieldsHeading.addOrGetChild(new FieldHelpItem(fieldName, fieldName, helpUrl)));
                }
                refresh();
            });
        }));
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

    public interface QueryHelpView extends View, HasUiHandlers<QueryHelpUiHandlers> {

        void setElementChooser(Widget widget);

        void setDetails(SafeHtml details);

        void enableButtons(boolean enable);
    }


    public static class DataSourceHelpItem extends QueryHelpItem {

        private final SafeHtml detail;

        public DataSourceHelpItem(final String title, final String detail, final String helpUrlBase) {
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
            return "\"" + text + "\"";
        }
    }

    public static class FieldHelpItem extends QueryHelpItem {

        private final SafeHtml detail;

        public FieldHelpItem(final String title, final String detail, final String helpUrlBase) {
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
            return "\"" + text + "\"";
        }
    }
}
