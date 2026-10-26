/*
 * Copyright 2025 Crown Copyright
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
import stroom.dispatch.client.RestErrorHandler;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.FindResultLists;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public class FindAnnotationListPresenter extends MyPresenterWidget<PagerPanel> {

    private final DataGrid<Annotation> resultList;
    private final RestDataProvider<Annotation, ResultPage<Annotation>> dataProvider;
    private final MultiSelectionModelImpl<Annotation> selectionModel;

    private DocumentPermission permission = DocumentPermission.EDIT;
    private Long sourceId;
    private Long destinationId;
    private String lastFilter;
    private Consumer<String> filterErrorConsumer;
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
                                       final PagerPanel view,
                                       final AnnotationResourceClient resourceClient,
                                       final DurationLabel durationLabel) {
        super(eventBus, view);

        selectionModel = new MultiSelectionModelImpl<>();

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
                    // A rejected filter comes back as an empty page like any other, so surface the
                    // reason on the filter box. See ResultPage.filterError.
                    if (filterErrorConsumer != null) {
                        filterErrorConsumer.accept(
                                NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                    }
                    if (resultPage.getPageStart() != resultList.getPageStart()) {
                        resultList.setPageStart(resultPage.getPageStart());
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

        // A striped list of a page of results, the one with the focus chosen; Enter, or a double
        // click, opens it, and Up on the first goes back to the filter
        resultList = FindResultLists.create(
                "Annotations found",
                new FindAnnotationCell(durationLabel),
                selectionModel,
                doc -> findResultListHandler.openDocument(doc),
                () -> findResultListHandler.focus());
        view.setDataWidget(resultList);
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
            dataProvider.addDataDisplay(resultList);
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
        resultList.setKeyboardSelectedRow(row, stealFocus);
    }

    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }

    public void setFilterErrorConsumer(final Consumer<String> filterErrorConsumer) {
        this.filterErrorConsumer = filterErrorConsumer;
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
