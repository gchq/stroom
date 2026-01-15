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

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.MyCellTable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.SelectionEventManager;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Event;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.function.Consumer;

public class FindAnnotationListPresenter extends MyPresenterWidget<PagerView> {

    private final CellTable<Annotation> cellTable;
    private final RestDataProvider<Annotation, ResultPage<Annotation>> dataProvider;
    private final MultiSelectionModelImpl<Annotation> selectionModel;

    private DocumentPermission permission = DocumentPermission.EDIT;
    private Long sourceId;
    private Long destinationId;
    private String lastFilter;
    private String filter;
    private boolean initialised;
    private boolean focusText;

    private FindDocResultListHandler<Annotation> findResultListHandler = new FindDocResultListHandler<Annotation>() {
        @Override
        public void openDocument(final Annotation match) {

        }

        @Override
        public void focus() {

        }
    };

    @Inject
    public FindAnnotationListPresenter(final EventBus eventBus,
                                       final PagerView view,
                                       final AnnotationResourceClient resourceClient,
                                       final DurationLabel durationLabel) {
        super(eventBus, view);

        cellTable = new MyCellTable<Annotation>(100) {
            @Override
            protected void onBrowserEvent2(final Event event) {
                super.onBrowserEvent2(event);
                if (event.getTypeInt() == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_UP) {
                    if (cellTable.getKeyboardSelectedRow() == 0) {
                        findResultListHandler.focus();
                    }
                }
            }
        };
        cellTable.addStyleName("FindCellTable");

        selectionModel = new MultiSelectionModelImpl<>();
        final SelectionEventManager<Annotation> selectionEventManager = new SelectionEventManager<>(
                cellTable,
                selectionModel,
                doc -> findResultListHandler.openDocument(doc),
                null);
        cellTable.setSelectionModel(selectionModel, selectionEventManager);

        dataProvider = new RestDataProvider<Annotation, ResultPage<Annotation>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Annotation>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final boolean filterChange = !Objects.equals(lastFilter, filter);
                lastFilter = filter;

                final FindAnnotationRequest request = new FindAnnotationRequest(
                        pageRequest,
                        null,
                        filter,
                        permission,
                        sourceId,
                        destinationId);
                resourceClient.findAnnotations(request, resultPage -> {
                    if (resultPage.getPageStart() != cellTable.getPageStart()) {
                        cellTable.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);

                    if (filterChange) {
                        if (!resultPage.isEmpty()) {
                            selectionModel.setSelected(resultPage.getValues().get(0));
                        } else {
                            selectionModel.clear();
                        }
                    }

                    resetFocus();
                }, errorHandler, getView());
            }
        };

        final Column<Annotation, Annotation> column = new Column<Annotation, Annotation>(
                new FindAnnotationCell(durationLabel)) {
            @Override
            public Annotation getValue(final Annotation object) {
                return object;
            }
        };
        cellTable.addColumn(column);
        view.setDataWidget(cellTable);
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            findResultListHandler.focus();
        }
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(cellTable);
        } else {
            dataProvider.refresh();
        }
    }

    public void setFindResultListHandler(final FindDocResultListHandler<Annotation> findResultListHandler) {
        this.findResultListHandler = findResultListHandler;
    }

    public Annotation getSelected() {
        return selectionModel.getSelected();
    }

    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        cellTable.setKeyboardSelectedRow(row, stealFocus);
    }

    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }

    public void setPermission(final DocumentPermission permission) {
        this.permission = permission;
    }

    public void setSourceId(final Long sourceId) {
        this.sourceId = sourceId;
    }

    public void setDestinationId(final Long destinationId) {
        this.destinationId = destinationId;
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }
}
