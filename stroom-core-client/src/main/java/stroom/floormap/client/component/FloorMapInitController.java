/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocInitialisationHandler;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.floormap.client.presenter.FloorMapQueryBuilder;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapResource;
import stroom.floormap.shared.ValueFormat;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Initialises a new [FloorMapDoc] ([FloorMapInit]), in a dialog made for one use: what
/// [DocInitialisationHandler] asks for once a floor map has been created.
///
/// The dialog asks for both a **Facts Store** — a [SqlTemporalStoreDoc], because the Editor
/// tab writes spatial data back to it — and an **Events Store** — a [FloorMapEventStoreDoc],
/// which is only ever read. The OK button stays disabled until both are chosen.
///
/// The two stores are deliberately *not* interchangeable: facts are edited in place through
/// `SqlTemporalStoreResource`, which only the SQL Temporal Store implements, while events are
/// append-only ingest and belong in a [FloorMapEventStoreDoc] — a Plan B temporal state store
/// whose key schema, temporal precision and value schema are fixed by the type. Each picker
/// therefore admits exactly one document type.
///
/// At query time the floor map references each store by *name* only — the name is substituted
/// into the `param('FactStore')` / `param('EventStore')` placeholders of the stored queries.
///
/// The default events query this dialog writes selects `EffectiveTime`, `Key` and `Value`, which
/// only a temporal state store exposes. A [FloorMapEventStoreDoc] always has that state type, so
/// restricting the picker to that document type is the whole check; nothing further is fetched or
/// validated on OK.
///
/// On OK: the new document is patched with the chosen store references and saved. On Cancel: the
/// document is deleted from the explorer.
public class FloorMapInitController extends Controller {

    private static final FloorMapResource FLOOR_MAP_RESOURCE = GWT.create(FloorMapResource.class);
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    /// Default dialog width in pixels.
    private static final int DIALOG_WIDTH = 320;
    /// Default dialog height in pixels.
    private static final int DIALOG_HEIGHT = 300;

    private final DocSelectionController factsStore;
    private final DocSelectionController eventsStore;
    private final RestFactory restFactory;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the two store pickers.
    /// @param restFactory          Saves or deletes the new document.
    @Inject
    public FloorMapInitController(final EventBus eventBus,
                                  final FloorMapInit view,
                                  final Provider<DocSelectionController> docSelectionProvider,
                                  final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
        initWidget(view);

        // Facts Store = SqlTemporalStore (the Editor tab writes to it)
        factsStore = make(docSelectionProvider);
        factsStore.attach(view.getFactsStoreBox());
        factsStore.setCaption("Choose Facts Store");
        factsStore.setIncludedTypes(SqlTemporalStoreDoc.TYPE);
        factsStore.setRequiredPermissions(DocumentPermission.USE);

        // Events Store = FloorMapEventStore (read-only; the type fixes its state type)
        eventsStore = make(docSelectionProvider);
        eventsStore.attach(view.getEventsStoreBox());
        eventsStore.setCaption("Choose Events Store");
        eventsStore.setIncludedTypes(FloorMapEventStoreDoc.TYPE);
        eventsStore.setRequiredPermissions(DocumentPermission.USE);

        own(factsStore.addSelectionListener(docRef -> validate()));
        own(eventsStore.addSelectionListener(docRef -> validate()));
    }

    /// Shows the dialog for a floor map created moments ago, owned by `opener` until it is closed,
    /// as [DocInitialisationHandler#showInitialisationDialog] describes. OK saves the chosen
    /// stores in it; Cancel deletes it.
    ///
    /// @param opener             What shows it.
    /// @param docRef             The new document, which exists on the server.
    /// @param onComplete         Given `true` once the document is saved, or `false` once it has
    ///                           been deleted (or deleting it was tried).
    /// @param taskMonitorFactory Shows the save or delete as busy.
    public void show(final HasControllerNode opener,
                     final DocRef docRef,
                     final Consumer<Boolean> onComplete,
                     final TaskMonitorFactory taskMonitorFactory) {
        Objects.requireNonNull(docRef, "docRef must not be null");
        Objects.requireNonNull(onComplete, "onComplete must not be null");
        Objects.requireNonNull(taskMonitorFactory, "taskMonitorFactory must not be null");

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Initialise New Floor Map")
                .onShow(e -> {
                    factsStore.focus();
                    validate();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        applyInitialisation(e, docRef, onComplete, taskMonitorFactory);
                    } else {
                        deleteAndAbort(e, docRef, onComplete, taskMonitorFactory);
                    }
                })
                .fire();
    }

    // Enables OK only when both stores are chosen. The pickers themselves constrain each choice to
    // a valid store type, so no further check is needed.
    private void validate() {
        final boolean valid = factsStore.getSelectedEntityReference() != null
                              && eventsStore.getSelectedEntityReference() != null;
        ControllerDialog.setActionEnabled(this, DialogAction.OK, valid);
    }

    private void applyInitialisation(final HidePopupRequestEvent e,
                                     final DocRef docRef,
                                     final Consumer<Boolean> onComplete,
                                     final TaskMonitorFactory taskMonitorFactory) {
        final DocRef factsDocRef = factsStore.getSelectedEntityReference();
        final DocRef eventsDocRef = eventsStore.getSelectedEntityReference();
        if (factsDocRef == null || eventsDocRef == null) {
            // The OK button stays disabled until both are chosen, but OK is reachable by other
            // routes than the button, so the dialog must not save a map with no stores whatever
            // the route. Reset, or the dialog is left with both buttons dead.
            AlertEvent.fireWarn(this, "Choose both a Facts Store and an Events Store.", e::reset);
            return;
        }

        // No state-type check: the picker is restricted to FloorMapEventStoreDoc, and that type
        // always carries TEMPORAL_STATE - its constructor sets it regardless of what it is given.
        saveInitialisation(e, docRef, factsDocRef, eventsDocRef, onComplete, taskMonitorFactory);
    }

    // Patches the new document with the chosen store references and the default queries, then
    // saves it. The events query written here selects EffectiveTime, which every
    // FloorMapEventStoreDoc records: the type fixes its state type, so there is nothing to confirm
    // first.
    private void saveInitialisation(final HidePopupRequestEvent e,
                                    final DocRef docRef,
                                    final DocRef factsDocRef,
                                    final DocRef eventsDocRef,
                                    final Consumer<Boolean> onComplete,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(FLOOR_MAP_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(doc -> {
                    final FloorMapDoc updated = doc.copy()
                            .factsStoreRef(factsDocRef)
                            .eventsStoreRef(eventsDocRef)
                            .eventsQuery(FloorMapEventsQuery.defaultQuery())
                            // Seeded rather than generated on demand, so they are visible and
                            // editable from the moment the document exists. Both fall back to these
                            // same defaults if ever cleared.
                            .histogramQuery(FloorMapQueryBuilder.defaultHistogramQuery())
                            .extentQuery(FloorMapQueryBuilder.defaultExtentQuery())
                            // The query above aliases exactly these columns; without the mapping
                            // the parse matches nothing and no entity reaches the canvas. Both are
                            // generated from FloorMapEventRole, so they cannot disagree.
                            .eventColumns(FloorMapEventColumns.defaults())
                            .valueFormat(ValueFormat.JSON)
                            .valueSchema(FloorMapFieldMapping.initialValueSchema())
                            .build();

                    restFactory
                            .create(FLOOR_MAP_RESOURCE)
                            .method(res -> res.update(updated.getUuid(), updated))
                            .onSuccess(savedDoc -> {
                                e.hide();
                                onComplete.accept(true);
                            })
                            .onFailure(error -> e.reset())
                            .owner(this)
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                })
                .onFailure(error -> e.reset())
                .owner(this)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    // Deletes the document created moments ago and tells the caller it was abandoned, once the
    // explorer tree has been told to drop it. The dialog is hidden (and this disposed) first, so the
    // reply isn't tied to this.
    private void deleteAndAbort(final HidePopupRequestEvent e,
                                final DocRef docRef,
                                final Consumer<Boolean> onComplete,
                                final TaskMonitorFactory taskMonitorFactory) {
        final EventBus eventBus = getEventBus();
        e.hide();
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.delete(new ExplorerServiceDeleteRequest(List.of(docRef))))
                .onSuccess(result -> {
                    RefreshExplorerTreeEvent.fire(eventBus::fireEvent);
                    onComplete.accept(false);
                })
                // Best effort - still abort
                .onFailure(error -> onComplete.accept(false))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
