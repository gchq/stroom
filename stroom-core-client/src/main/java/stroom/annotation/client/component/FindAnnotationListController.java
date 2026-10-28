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

package stroom.annotation.client.component;

import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.client.DurationLabel;
import stroom.annotation.client.FindAnnotationCell;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The annotations found ([FindAnnotationList]) by a filter, and linked to or from an annotation
/// if given one, a page at a time; the first chosen when the filter changes.
public class FindAnnotationListController extends Controller {

    private final FindAnnotationList view;
    private final RestDataProvider<Annotation, ResultPage<Annotation>> dataProvider;

    private DocumentPermission permission = DocumentPermission.EDIT;
    private Long sourceId;
    private Long destinationId;
    private String lastFilter;
    private Consumer<String> onFilterError = error -> {
    };
    private Runnable onFocusFilter = () -> {
    };
    private String filter;
    private boolean initialised;
    private boolean focusText;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus       The application's event bus.
    /// @param view           The content.
    /// @param resourceClient Finds the annotations.
    /// @param durationLabel  Says how long ago an annotation changed.
    @Inject
    public FindAnnotationListController(final EventBus eventBus,
                                        final FindAnnotationList view,
                                        final AnnotationResourceClient resourceClient,
                                        final DurationLabel durationLabel) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setCell(new FindAnnotationCell(durationLabel));
        view.setOnUpFromFirst(() -> onFocusFilter.run());

        final MultiSelectionModel<Annotation> selectionModel = view.getSelectionModel();

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
                resourceClient.findAnnotations(FindAnnotationListController.this, request, resultPage -> {
                    // A rejected filter comes back as an empty page like any other, so surface the
                    // reason on the filter box. See ResultPage.filterError.
                    onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                    if (resultPage.getPageStart() != view.getPageStart()) {
                        view.setPageStart(resultPage.getPageStart());
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
                }, errorHandler, view.getTaskMonitor());
            }
        };
    }

    private void resetFocus() {
        if (focusText) {
            focusText = false;
            onFocusFilter.run();
        }
    }

    /// Finds the annotations again; the first time, starts the list.
    public void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// @param onOpen Given an annotation to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<Annotation> onOpen) {
        view.setOnOpen(onOpen);
    }

    /// @param onFocusFilter Run to put the focus back on the filter: on Up from the first
    ///                      annotation, and after a find asked for with [#setFocusText(boolean)].
    public void setOnFocusFilter(final Runnable onFocusFilter) {
        this.onFocusFilter = onFocusFilter;
    }

    /// @return The annotation with the focus, or `null`.
    public Annotation getSelected() {
        return view.getSelectionModel().getSelected();
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Moves the focus to a row.
    ///
    /// @param row        The row on the page.
    /// @param stealFocus Whether to take the focus from where it is.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        view.setKeyboardSelectedRow(row, stealFocus);
    }

    /// @param focusText `true` to put the focus on the filter once the next find comes back.
    public void setFocusText(final boolean focusText) {
        this.focusText = focusText;
    }

    /// @param onFilterError Given why the filter was rejected after each find, or `null` if it
    ///                      wasn't.
    public void setOnFilterError(final Consumer<String> onFilterError) {
        this.onFilterError = onFilterError;
    }

    /// @param bordered Whether the list has a field's border and background, as in a dialog.
    public void setBordered(final boolean bordered) {
        view.setBordered(bordered);
    }

    /// @param filter The filter's text, for the next find.
    public void setFilter(final String filter) {
        this.filter = filter;
    }

    /// @param permission The permission the user must have on an annotation for it to be listed.
    public void setPermission(final DocumentPermission permission) {
        this.permission = permission;
    }

    /// @param sourceId Lists only annotations linked from this annotation, or `null` for any.
    public void setSourceId(final Long sourceId) {
        this.sourceId = sourceId;
    }

    /// @param destinationId Lists only annotations linked to this annotation, or `null` for any.
    public void setDestinationId(final Long destinationId) {
        this.destinationId = destinationId;
    }

    /// @param handler Told when another annotation is chosen.
    /// @return Its registration.
    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return view.getSelectionModel().addSelectionHandler(handler);
    }
}
