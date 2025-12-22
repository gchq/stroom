/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.elastic.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.preferences.client.DateTimeFormatter;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter.ElasticIndexFieldListView;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ElasticIndexFieldListPresenter extends DocumentEditPresenter<ElasticIndexFieldListView, ElasticIndexDoc> {

    private final MyDataGrid<ElasticIndexField> dataGrid;
    private final DateTimeFormatter dateTimeFormatter;
    private List<ElasticIndexField> fields;
    private ElasticIndexFieldDataProvider<ElasticIndexField> dataProvider;

    @Inject
    public ElasticIndexFieldListPresenter(final EventBus eventBus,
                                          final ElasticIndexFieldListView view,
                                          final PagerView pagerView,
                                          final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        view.setDataGridView(pagerView);

        addColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
    }

    private void addColumns() {
        addStringColumn("Name", 300, ElasticIndexField::getFldName);
        addStringColumn("Field Type", 150, row -> row.getFldType().getDisplayValue());
        addStringColumn("Native Type", 150, ElasticIndexField::getNativeType);
        addBooleanColumn("Indexed", 100, ElasticIndexField::isIndexed);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addStringColumn(final String name,
                                 final int width,
                                 final Function<ElasticIndexField, String> function) {
        dataGrid.addResizableColumn(new Column<ElasticIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final ElasticIndexField row) {
                return function.apply(row);
            }
        }, name, width);
    }

    private void addBooleanColumn(final String name,
                                  final int width,
                                  final Function<ElasticIndexField, Boolean> function) {
        dataGrid.addResizableColumn(new Column<ElasticIndexField, String>(new TextCell()) {
            @Override
            public String getValue(final ElasticIndexField row) {
                return getYesNoString(function.apply(row));
            }
        }, name, width);
    }

    private String getYesNoString(final boolean bool) {
        if (bool) {
            return "Yes";
        }
        return "No";
    }

    private void refresh() {
        if (fields == null) {
            fields = new ArrayList<>();
        }

        if (dataProvider == null) {
            this.dataProvider = new ElasticIndexFieldDataProvider<>();
            dataProvider.addDataDisplay(dataGrid);
        }

        dataProvider.setList(fields);
        dataProvider.refresh();
    }

    @Override
    protected void onRead(final DocRef docRef, final ElasticIndexDoc document, final boolean readOnly) {
        if (document != null) {
            fields = document.getFields().stream()
                    .sorted(Comparator.comparing(ElasticIndexField::getFldName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            final StringBuilder sb = new StringBuilder();
            sb
                    .append("Field list updated at: ")
                    .append(dateTimeFormatter.format(System.currentTimeMillis()));

            getView().setStatusMessage(sb.toString());
        }

        refresh();
    }

    @Override
    protected ElasticIndexDoc onWrite(final ElasticIndexDoc document) {
        return document;
    }

    public interface ElasticIndexFieldListView extends View {

        void setDataGridView(final View view);

        void setStatusMessage(final String syncState);
    }
}
