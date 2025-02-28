/*
 * Copyright 2016 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.shared.AnnotationGroup;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class AnnotationGroupListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<AnnotationGroup> dataGrid;
    private final MultiSelectionModelImpl<AnnotationGroup> selectionModel;
    private final RestDataProvider<AnnotationGroup, ResultPage<AnnotationGroup>> dataProvider;

    @Inject
    public AnnotationGroupListPresenter(final EventBus eventBus,
                                        final PagerView view,
                                        final AnnotationResourceClient annotationResourceClient) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");

        initTableColumns();

        final ExpressionCriteria criteria = new ExpressionCriteria();
        dataProvider = new RestDataProvider<AnnotationGroup, ResultPage<AnnotationGroup>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AnnotationGroup>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                CriteriaUtil.setRange(criteria, range);
                annotationResourceClient.findAnnotationGroups(criteria, dataConsumer, errorHandler, view);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<AnnotationGroup, String> nameColumn = new Column<AnnotationGroup, String>(new TextCell()) {
            @Override
            public String getValue(final AnnotationGroup annotationGroup) {
                return annotationGroup.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 400);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public MultiSelectionModel<AnnotationGroup> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
