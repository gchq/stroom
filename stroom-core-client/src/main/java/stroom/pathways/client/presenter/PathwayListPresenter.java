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

package stroom.pathways.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FetchPathwayRequest;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwaySummary;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.Pathway;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class PathwayListPresenter
        extends DocPresenter<QuickFilterPageView, PathwaysDoc>
        implements QuickFilterUiHandlers {

    private static final PathwaysResource PATHWAYS_RESOURCE = GWT.create(PathwaysResource.class);

    private final DateTimeFormatter dateTimeFormatter;
    private final PagerView pagerView;
    private final RestFactory restFactory;
    private final MyDataGrid<PathwaySummary> dataGrid;
    private final MultiSelectionModelImpl<PathwaySummary> selectionModel;
    private final PathwayTabManager pathwayTabManager;
    private final ButtonView newButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private RestDataProvider<PathwaySummary, ResultPage<PathwaySummary>> dataProvider;
    private String selectedName;
    // The row the reader picked, by name. Held apart from the selection model because a refresh brings
    // back new summaries and the model matches them by equality — and a summary carries readings that
    // move, so the row that comes back is never equal to the one that went, however plainly it is the
    // same pathway.
    private String wantedSelection;
    private boolean fetching;
    private long requests;
    private final List<Consumer<Pathway>> waiting = new ArrayList<>();

    private String filter;
    private DocRef docRef;
    private PathwaysDoc pathwaysDoc;
    private boolean readOnly = true;

    @Inject
    public PathwayListPresenter(final EventBus eventBus,
                                final QuickFilterPageView view,
                                final PagerView pagerView,
                                final RestFactory restFactory,
                                final DateTimeFormatter dateTimeFormatter,
                                final PathwayTabManager pathwayTabManager) {
        super(eventBus, view);
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        view.setDataView(pagerView);
        view.setUiHandlers(this);

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("Pathways");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        this.pathwayTabManager = pathwayTabManager;

        newButton = pagerView.addButton(SvgPresets.NEW_ITEM);
        editButton = pagerView.addButton(SvgPresets.EDIT);
        removeButton = pagerView.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Remembered by name as the reader moves, including when they move off everything, so a
        // refresh puts them back where they were rather than on whatever used to be there.
        registerHandler(selectionModel.addSelectionHandler(event ->
                wantedSelection = NullSafe.get(selectionModel.getSelected(), PathwaySummary::getName)));

        registerHandler(newButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onAdd();
                }
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onEdit();
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (!readOnly) {
                if (MouseUtil.isPrimary(event)) {
                    onRemove();
                }
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            if (!readOnly) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit();
                }
            }
        }));
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    public MultiSelectionModelImpl<PathwaySummary> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public void onFilterChange(final String text) {
        filter = text;
        refresh();
    }

    private void enableButtons() {
        newButton.setEnabled(!readOnly);
        if (!readOnly) {
            final PathwaySummary selectedElement = selectionModel.getSelected();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }

        if (readOnly) {
            newButton.setTitle("New pathway disabled as read only");
            editButton.setTitle("Edit pathway disabled as read only");
            removeButton.setTitle("Remove pathway disabled as read only");
        } else {
            newButton.setTitle("New Pathway");
            editButton.setTitle("Edit Pathway");
            removeButton.setTitle("Remove Pathway");
        }
    }

    private void addColumns() {
        addNameColumn();
        addCreateTimeColumn();
        addUpdateTimeColumn();
        addLastUsedColumn();
        addTimesUsedColumn();
        addTimesUpdatedColumn();
        addNodesColumn();
        addPathsColumn();
        addSizeColumn();
    }

    // How wide the model is, against Size which is how heavy. A handful of nodes taking a lot of room
    // is a pathway carrying large constraint values rather than much structure, which is the one shape
    // the size on its own cannot be read for.
    private void addNodesColumn() {
        addIntColumn(PathwaySummary.FIELD_NODES, PathwaySummary::getNodes);
    }

    // How many distinct ways traces have gone through. Against Traces it says whether the ways through
    // have settled or are still being found.
    private void addPathsColumn() {
        addIntColumn(PathwaySummary.FIELD_PATHS, PathwaySummary::getPaths);
    }

    private void addIntColumn(final String name, final Function<PathwaySummary, Integer> count) {
        final Column<PathwaySummary, String> column = DataGridUtil
                .textColumnBuilder((PathwaySummary summary) ->
                        ModelStringUtil.formatCsv((long) count.apply(summary)))
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column, name, ColumnSizeConstants.SMALL_COL);
    }

    // A pathway keeps every path it has seen, so they differ by orders of magnitude and the large ones
    // are slow to open. Free to show: it is the stored length of the value the row was read from.
    private void addSizeColumn() {
        final Column<PathwaySummary, String> column = DataGridUtil
                .textColumnBuilder((PathwaySummary summary) ->
                        ModelStringUtil.formatIECByteSizeString(summary.getSizeBytes()))
                .withSorting(PathwaySummary.FIELD_SIZE)
                .build();
        dataGrid.addResizableColumn(column, PathwaySummary.FIELD_SIZE, ColumnSizeConstants.SMALL_COL);
    }

    private void addNameColumn() {
        final Column<PathwaySummary, String> column = DataGridUtil.textColumnBuilder(PathwaySummary::getName)
                .withSorting(PathwaySummary.FIELD_NAME)
                .build();
        dataGrid.addResizableColumn(column,
                PathwaySummary.FIELD_NAME,
                500);
//        dataGrid.sort(column);
    }

    private void addCreateTimeColumn() {
        addTimeColumn(PathwaySummary.FIELD_CREATE_TIME, PathwaySummary::getCreateTime);
    }

    private void addUpdateTimeColumn() {
        addTimeColumn(PathwaySummary.FIELD_UPDATE_TIME, PathwaySummary::getUpdateTime);
    }

    private void addLastUsedColumn() {
        addTimeColumn(PathwaySummary.FIELD_LAST_USED_TIME, PathwaySummary::getLastUsedTime);
    }

    // How many traces have taken this path, beside the time the last one did. A path used once an
    // hour and one used a thousand times an hour read the same from Last Used alone.
    private void addTimesUsedColumn() {
        addCountColumn(PathwaySummary.FIELD_TIMES_USED, PathwaySummary::getTimesUsed);
    }

    // How many of those traces taught the model something, which is how many times Update Time moved
    // after the first. Beside Traces it says whether a pathway is still being learnt or has settled,
    // which neither count says on its own.
    private void addTimesUpdatedColumn() {
        addCountColumn(PathwaySummary.FIELD_TIMES_UPDATED, PathwaySummary::getTimesUpdated);
    }

    private void addCountColumn(final String name, final Function<PathwaySummary, Long> count) {
        final Column<PathwaySummary, String> column = DataGridUtil
                .textColumnBuilder((PathwaySummary summary) -> ModelStringUtil.formatCsv(count.apply(summary)))
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column, name, ColumnSizeConstants.SMALL_COL);
    }

    private void addTimeColumn(final String name, final Function<PathwaySummary, NanoTime> function) {
        final Function<PathwaySummary, String> valueExtractor = summary -> {
            final NanoTime nanoTime = function.apply(summary);
            return nanoTime == null
                    ? ""
                    : dateTimeFormatter.format(nanoTime.toEpochMillis());
        };
        final Column<PathwaySummary, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                ColumnSizeConstants.DATE_COL);
//        dataGrid.sort(column);
    }

    private void onAdd() {
        // Made first and opened afterwards, rather than built in a dialog and made on OK. A tab has no
        // OK, and a pathway that exists is the only thing a tab can be opened on.
        final NanoTime now = NanoTime.ofMillis(System.currentTimeMillis());
        final Pathway pathway = Pathway.builder().name("").createTime(now).build();
        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.addPathway(new AddPathway(docRef, pathway)))
                .onSuccess(response -> {
                    refresh();
                    pathwayTabManager.open(pathwaysDoc, pathway.getName(), readOnly, () -> {
                        forget();
                        refresh();
                    });
                })
                .onFailure(new DefaultErrorHandler(this, null))
                .taskMonitorFactory(pagerView)
                .exec();
    }

    private void onEdit() {
        final PathwaySummary selected = selectionModel.getSelected();
        if (selected != null && pathwaysDoc != null) {
            // The tab fetches the model itself. The copy held here is shared with the drawing beside
            // this list, and constraints are edited straight into whatever model they were given, so
            // handing this one over would put unsaved edits on a screen that never asked for them.
            pathwayTabManager.open(pathwaysDoc, selected.getName(), readOnly, () -> {
                // The row shows a size and a time that a save has just moved.
                forget();
                refresh();
            });
        }
    }

    /**
     * The model for the row being looked at.
     *
     * <p>Fetched afresh each time it is asked for, including for the row already selected. A click on
     * a row is the reader asking to see it, and answering with what was fetched when they last looked
     * would draw minutes-old facts against the clock as it stands: the drawing colours a node by how
     * long since it last changed, so the same model put up again comes back older without anything
     * having been learnt about it.
     *
     * <p>What is held is only a fetch already on its way. The row carries nothing but its size — see
     * {@link PathwaySummary} — so everything around it wants the model, and they share one request
     * rather than making one each.
     */
    public void withSelectedPathway(final Consumer<Pathway> consumer) {
        final String name = NullSafe.get(selectionModel.getSelected(), PathwaySummary::getName);

        if (name == null) {
            forget();
            consumer.accept(null);
            return;
        }

        if (name.equals(selectedName) && fetching) {
            // One is already on its way for this row. Everyone asking is told when it lands, so a row
            // is fetched once however many parts of the view want it.
            waiting.add(consumer);
            return;
        }

        forget();
        selectedName = name;
        fetching = true;
        waiting.add(consumer);
        // Counted rather than matched on the name: selecting a row, leaving it and coming back starts
        // a second fetch for the same name, and the first one landing late must not answer for it.
        requests++;
        final long request = requests;

        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.fetchPathway(new FetchPathwayRequest(docRef, name)))
                .onSuccess(pathway -> {
                    if (request == requests) {
                        fetching = false;
                        tell(pathway);
                    }
                })
                .onFailure(new DefaultErrorHandler(this, () -> {
                    if (request == requests) {
                        // Told, not dropped. Otherwise the view around this one keeps showing the model
                        // for the row before, beside a different row highlighted.
                        failed();
                    }
                }))
                .taskMonitorFactory(pagerView)
                .exec();
    }

    private void tell(final Pathway pathway) {
        final List<Consumer<Pathway>> toTell = new ArrayList<>(waiting);
        waiting.clear();
        toTell.forEach(consumer -> consumer.accept(pathway));
    }

    private void failed() {
        final List<Consumer<Pathway>> toTell = new ArrayList<>(waiting);
        forget();
        toTell.forEach(consumer -> consumer.accept(null));
    }

    private void forget() {
        selectedName = null;
        fetching = false;
        waiting.clear();
    }

    private void onRemove() {
        final List<PathwaySummary> list = selectionModel.getSelectedItems();
        if (list != null && !list.isEmpty()) {
            String message = "Are you sure you want to delete the selected pathway?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected pathways?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    deleteNext(new ArrayList<>(list), 0);
                }
            });
        }
    }

    // One at a time, each starting when the one before it has come back.
    //
    // Forgetting a pathway takes the cluster lock on the shard it lives in, and holds it for as long
    // as it takes to bring that shard down, change it and put it back. Asked for all at once, two
    // pathways of the same shard queue behind one another on that lock while both of them sit on a
    // server thread waiting — and a reader deleting a dozen would have a dozen requests waiting on
    // one lock, with the browser holding most of them back as well for its own reasons.
    //
    // Stopping on the first failure rather than going on: these all want the same handful of locks,
    // so what stops one stops the rest, and a reader does not want the same message a dozen times.
    // The list is read once here, because clearing the selection at the end would otherwise take the
    // rest of the work with it.
    private void deleteNext(final List<PathwaySummary> list, final int at) {
        if (at >= list.size()) {
            // Nothing to go back to: what was selected is what was just removed.
            wantedSelection = null;
            selectionModel.clear();
            refresh();
            return;
        }

        final String name = list.get(at).getName();
        restFactory
                .create(PATHWAYS_RESOURCE)
                .method(res -> res.deletePathway(new DeletePathway(docRef, name)))
                .onSuccess(response -> deleteNext(list, at + 1))
                // Said once and then given up on. The handler shows what went wrong; this puts the
                // list back to what the store actually holds, which is however far it got.
                .onFailure(new DefaultErrorHandler(this, () -> {
                    selectionModel.clear();
                    refresh();
                }))
                .taskMonitorFactory(pagerView)
                .exec();
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document, final boolean readOnly) {
        // A different document holds different pathways, so what was fetched for the last one says
        // nothing about this one.
        forget();
        this.docRef = docRef;
        this.pathwaysDoc = document;
        this.readOnly = readOnly;
        enableButtons();
        refresh();
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return document;
    }

    // Puts the reader back on the row they were on, found by name among the ones that have just
    // arrived. Told to no one: the row has not changed, only the object standing for it, and saying so
    // would have everything around this fetch the same pathway again.
    //
    // Whether something is selected is not the question — after a read the model can still be holding
    // the row that went, which is of the right name and in no list, so the grid shows nothing picked
    // while the model says otherwise. What is asked instead is whether what it holds is one of the
    // rows just handed over.
    private void reselect(final String wanted, final List<PathwaySummary> values) {
        if (wanted == null) {
            return;
        }
        final List<PathwaySummary> rows = NullSafe.list(values);
        final PathwaySummary selected = selectionModel.getSelected();
        if (selected != null && wanted.equals(selected.getName()) && rows.contains(selected)) {
            return;
        }
        for (final PathwaySummary summary : rows) {
            if (wanted.equals(summary.getName())) {
                selectionModel.setSelected(summary, true, new SelectionType(), false);
                wantedSelection = wanted;
                return;
            }
        }
    }

    private void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<PathwaySummary, ResultPage<PathwaySummary>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<PathwaySummary>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final FindPathwayCriteria criteria = new FindPathwayCriteria(
                            CriteriaUtil.createPageRequest(range),
                            CriteriaUtil.createSortList(dataGrid.getColumnSortList()),
                            docRef,
                            filter,
                            null);

                    restFactory
                            .create(PATHWAYS_RESOURCE)
                            .method(res -> res.findPathways(criteria))
                            .onSuccess(result -> {
                                // Taken before the rows are handed over, because handing them over can
                                // clear the selection — and clearing it is a change like any other, so
                                // what was wanted would be forgotten a moment before it was wanted.
                                final String wanted = wantedSelection;
                                dataConsumer.accept(
                                        new ResultPage<>(result.getValues(), result.getPageResponse()));
                                reselect(wanted, result.getValues());
                            })
                            .onFailure(errorHandler)
                            .taskMonitorFactory(pagerView)
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
    }
}
