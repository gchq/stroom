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

package stroom.annotation.client;

import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagFields;
import stroom.annotation.shared.AnnotationTagType;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class AnnotationTagListPresenter extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<AnnotationTag> dataGrid;
    private final MultiSelectionModelImpl<AnnotationTag> selectionModel;
    private final AnnotationResourceClient annotationResourceClient;

    private RestDataProvider<AnnotationTag, ResultPage<AnnotationTag>> dataProvider;
    private AnnotationTagType annotationTagType;

    @Inject
    public AnnotationTagListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final AnnotationResourceClient annotationResourceClient) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);
        getWidget().getElement().addClassName("default-min-sizes");
    }

    public MultiSelectionModel<AnnotationTag> getSelectionModel() {
        return selectionModel;
    }

    public void refresh() {
        if (dataProvider == null) {
            initTableColumns();

            dataProvider = new RestDataProvider<AnnotationTag, ResultPage<AnnotationTag>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<AnnotationTag>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final ExpressionOperator expression = ExpressionOperator
                            .builder()
                            .addTerm(ExpressionTerm.builder()
                                    .field(AnnotationTagFields.TYPE_ID)
                                    .condition(Condition.EQUALS)
                                    .value(annotationTagType.getDisplayValue())
                                    .build())
                            .build();
                    final ExpressionCriteria criteria = new ExpressionCriteria(expression);
                    CriteriaUtil.setRange(criteria, range);
                    annotationResourceClient.findAnnotationTags(criteria, dataConsumer, errorHandler, getView());
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Name.
        final Column<AnnotationTag, String> nameColumn = new Column<AnnotationTag, String>(new TextCell()) {
            @Override
            public String getValue(final AnnotationTag annotationTag) {
                return annotationTag.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 400);

        if (annotationTagType == AnnotationTagType.LABEL) {
            // Style.
            final Function<AnnotationTag, SafeHtml> function = annotationTag ->
                    ConditionalFormattingSwatchUtil.createSwatch(
                            ConditionalFormattingType.BACKGROUND,
                            annotationTag.getStyle(),
                            null,
                            null);
            dataGrid.addColumn(DataGridUtil.htmlColumnBuilder(function)
                            .build(),
                    DataGridUtil.headingBuilder("Style")
                            .withToolTip("The style the " + annotationTagType.getDisplayValue() + ".")
                            .build(),
                    200);
        }

        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setAnnotationTagType(final AnnotationTagType annotationTagType) {
        this.annotationTagType = annotationTagType;
    }
}
