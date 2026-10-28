/*
 * Copyright 2020 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.UpdatePathway;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// A Pathways document's pathways ([PathwayList]), a page at a time, found by the quick filter. New,
/// Edit and Delete change them on the server at once, unless the document is read only
/// ([Contexts#READ_ONLY]).
public class PathwayListController extends Controller {

    private static final PathwaysResource PATHWAYS_RESOURCE = GWT.create(PathwaysResource.class);

    private final PathwayList view;
    private final RestFactory restFactory;
    private final PathwayEditController pathwayEdit;
    private RestDataProvider<Pathway, ResultPage<Pathway>> dataProvider;

    private String filter;
    private DocRef docRef;
    private PathwaysDoc pathwaysDoc;
    private Consumer<Pathway> onSelectionChange = pathway -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param restFactory          Finds and changes the pathways.
    /// @param dateTimeFormatter    Formats the times shown.
    /// @param pathwayEdit          Edits a pathway.
    @Inject
    public PathwayListController(final EventBus eventBus,
                                 final PathwayList view,
                                 final RestFactory restFactory,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final PathwayEditController pathwayEdit) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.pathwayEdit = own(pathwayEdit);
        initWidget(view);
        view.addColumns(dateTimeFormatter::format);
        view.setOnFilterChange(this::onFilterChange);
        view.setOnNew(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        view.setOnSortChange(this::refresh);
        watch(Contexts.READ_ONLY, view::setReadOnly);
        own(view.getSelectionModel().addSelectionHandler(event ->
                onSelectionChange.accept(view.getSelectionModel().getSelected())));
    }

    /// @param onSelectionChange Given the pathway chosen, or `null` for none, each time the choice
    ///                          changes.
    public void setOnSelectionChange(final Consumer<Pathway> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    private void onFilterChange(final String text) {
        filter = text;
        refresh();
    }

    private void onAdd() {
        final NanoTime now = NanoTime.ofMillis(System.currentTimeMillis());
        pathwayEdit.read(pathwaysDoc, Pathway.builder().name("").createTime(now).build());
        pathwayEdit.show("New Pathway", e -> {
            if (e.isOk()) {
                final Pathway pathway = pathwayEdit.write();
                restFactory
                        .create(PATHWAYS_RESOURCE)
                        .method(res -> res.addPathway(new AddPathway(docRef, pathway)))
                        .onSuccess(response -> {
                            view.getSelectionModel().setSelected(pathway);
                            refresh();
                            e.hide();
                        })
                        .onFailure(new DefaultErrorHandler(this, e::reset))
                        .owner(this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            } else {
                e.hide();
            }
        });
    }

    private void onEdit() {
        final Pathway existingPathway = view.getSelectionModel().getSelected();
        if (existingPathway != null) {
            pathwayEdit.read(pathwaysDoc, existingPathway);
            pathwayEdit.show("Edit Pathway", e -> {
                if (e.isOk()) {
                    try {
                        final Pathway pathway = pathwayEdit.write();
                        restFactory
                                .create(PATHWAYS_RESOURCE)
                                .method(res -> res.updatePathway(new UpdatePathway(
                                        docRef,
                                        existingPathway.getName(),
                                        pathway)))
                                .onSuccess(response -> {
                                    view.getSelectionModel().setSelected(pathway);
                                    refresh();
                                    e.hide();
                                })
                                .onFailure(new DefaultErrorHandler(this, e::reset))
                                .owner(this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    } catch (final RuntimeException ex) {
                        AlertEvent.fireError(this, ex.getMessage(), e::reset);
                    }
                } else {
                    e.hide();
                }
            });
        }
    }

    private void onRemove() {
        final List<Pathway> list = view.getSelectionModel().getSelectedItems();
        if (list != null && !list.isEmpty()) {
            String message = "Are you sure you want to delete the selected pathway?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected pathways?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    for (final Pathway pathway : list) {
                        restFactory
                                .create(PATHWAYS_RESOURCE)
                                .method(res -> res.deletePathway(new DeletePathway(docRef, pathway.getName())))
                                .onSuccess(response -> {
                                    view.getSelectionModel().clear();
                                    refresh();
                                })
                                .owner(this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }
                }
            });
        }
    }

    /// Shows a Pathways document's pathways.
    ///
    /// @param docRef   The document.
    /// @param document The document's content.
    public void read(final DocRef docRef, final PathwaysDoc document) {
        this.docRef = docRef;
        this.pathwaysDoc = document;
        refresh();
    }

    private void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<Pathway, ResultPage<Pathway>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<Pathway>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final FindPathwayCriteria criteria = new FindPathwayCriteria(
                            CriteriaUtil.createPageRequest(range),
                            CriteriaUtil.createSortList(view.getSortList()),
                            docRef,
                            filter,
                            null);

                    restFactory
                            .create(PATHWAYS_RESOURCE)
                            .method(res -> res.findPathways(criteria))
                            .onSuccess(result ->
                                    dataConsumer.accept(new ResultPage<>(result.getValues(), result.getPageResponse())))
                            .onFailure(errorHandler)
                            .owner(PathwayListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }
}
