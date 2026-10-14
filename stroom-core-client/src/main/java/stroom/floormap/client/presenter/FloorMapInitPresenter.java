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

package stroom.floormap.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocInitialisationHandler;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerServiceDeleteRequest;
import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapResource;
import stroom.floormap.shared.ValueFormat;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Initialisation handler for new [FloorMapDoc] documents.
///
/// Displays a modal dialog requiring the user to select both a
/// **Facts Store** — a [SqlTemporalStoreDoc], because the Editor
/// tab writes spatial data back to it — and an **Events Store** — a
/// [FloorMapEventStoreDoc], which is only ever read. The OK button remains disabled until
/// both are selected.
///
/// The two stores are deliberately *not* interchangeable: facts are
/// edited in place through `SqlTemporalStoreResource`, which only the SQL
/// Temporal Store implements, while events are append-only ingest and belong in
/// a [FloorMapEventStoreDoc] — a Plan B temporal state store whose key schema,
/// temporal precision and value schema are fixed by the type. Each picker therefore
/// admits exactly one document type.
///
/// At query time the floor map references each store by *name* only —
/// the name is substituted into the `param('FactStore')` /
/// `param('EventStore')` placeholders of the stored queries.
///
/// The default events query this dialog writes selects `EffectiveTime`,
/// `Key` and `Value`, which only a temporal state store exposes. A
/// [FloorMapEventStoreDoc] always has that state type, so restricting the
/// picker to that document type is the whole check; nothing further is fetched or
/// validated on OK.
///
/// On OK: the new document is patched with the selected store
/// references and saved. On Cancel: the document is deleted from
/// the explorer.
///
/// @see DocInitialisationHandler
public class FloorMapInitPresenter
        extends MyPresenterWidget<FloorMapInitPresenter.FloorMapInitView>
        implements DocInitialisationHandler {

    private static final FloorMapResource FLOOR_MAP_RESOURCE =
            GWT.create(FloorMapResource.class);

    private static final ExplorerResource EXPLORER_RESOURCE =
            GWT.create(ExplorerResource.class);

    /// Default dialog width in pixels.
    private static final int DIALOG_WIDTH = 320;
    /// Default dialog height in pixels.
    private static final int DIALOG_HEIGHT = 300;

    private final DocSelectionBoxPresenter factsStorePresenter;
    private final DocSelectionBoxPresenter eventsStorePresenter;
    private final RestFactory restFactory;

    /// The DocRef of the newly created document being initialised. May be null when dialog is not showing.
    private DocRef docRef;

    /// Callback to signal the outcome to the caller. Never null while the dialog is showing.
    private Consumer<Boolean> completionCallback;

    /// Creates a new `FloorMapInitPresenter`.
    ///
    /// @param eventBus                        the event bus; never null
    /// @param view                            the view implementation; never null
    /// @param docSelectionBoxPresenterProvider provider for doc selection widgets;
    ///         never null
    /// @param restFactory                     factory for REST calls; never null
    @Inject
    public FloorMapInitPresenter(
            final EventBus eventBus,
            final FloorMapInitView view,
            final Provider<DocSelectionBoxPresenter> docSelectionBoxPresenterProvider,
            final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        // Facts Store = SqlTemporalStore (the Editor tab writes to it)
        factsStorePresenter = docSelectionBoxPresenterProvider.get();
        factsStorePresenter.setCaption("Choose Facts Store");
        factsStorePresenter.setIncludedTypes(SqlTemporalStoreDoc.TYPE);
        factsStorePresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setFactsStoreView(factsStorePresenter.getView());

        // Events Store = FloorMapEventStore (read-only; the type fixes its state type)
        eventsStorePresenter = docSelectionBoxPresenterProvider.get();
        eventsStorePresenter.setCaption("Choose Events Store");
        eventsStorePresenter.setIncludedTypes(FloorMapEventStoreDoc.TYPE);
        eventsStorePresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setEventsStoreView(eventsStorePresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        //noinspection unused e
        registerHandler(factsStorePresenter.addDataSelectionHandler(e -> validate()));
        //noinspection unused e
        registerHandler(eventsStorePresenter.addDataSelectionHandler(e -> validate()));
    }

    // -- Validation --

    /// Updates the OK button enabled state based on current validity.
    ///
    /// The OK button is enabled only when both the Facts Store and the
    /// Events Store have a non-null selection. The picker itself constrains
    /// each selection to a valid store type, so no further check is needed.
    private void validate() {
        final boolean factsOk =
                factsStorePresenter.getSelectedEntityReference() != null;
        final boolean eventsOk =
                eventsStorePresenter.getSelectedEntityReference() != null;
        final boolean valid = factsOk && eventsOk;
        if (valid) {
            EnablePopupEvent.builder(this)
                    .action(DialogAction.OK).fire();
        } else {
            DisablePopupEvent.builder(this)
                    .action(DialogAction.OK).fire();
        }
    }

    // -- DocInitialisationHandler --

    /// {@inheritDoc}
    ///
    /// Shows a modal dialog with Facts Store and Events Store pickers.
    /// The OK button is disabled until both are validly selected.
    ///
    /// Preconditions:
    ///
    /// - `docRef` must be non-null and refer to an existing
    ///   FloorMapDoc on the server.
    /// - `onComplete` must be non-null.
    /// - `taskMonitorFactory` must be non-null.
    ///
    /// Postconditions:
    ///
    /// - If OK is clicked: the FloorMapDoc has been patched with
    ///   the selected store refs and saved;
    ///   `onComplete.accept(true)` is called.
    /// - If Cancel is clicked: the FloorMapDoc has been deleted
    ///   from the explorer; `onComplete.accept(false)` is
    ///   called.
    @Override
    public void showInitialisationDialog(final DocRef docRef,
                                         final Consumer<Boolean> onComplete,
                                         final TaskMonitorFactory taskMonitorFactory) {
        Objects.requireNonNull(docRef, "docRef must not be null");
        Objects.requireNonNull(onComplete, "onComplete must not be null");
        Objects.requireNonNull(taskMonitorFactory, "taskMonitorFactory must not be null");

        this.docRef = docRef;
        this.completionCallback = onComplete;

        factsStorePresenter.setSelectedEntityReference(null, false);
        eventsStorePresenter.setSelectedEntityReference(null, false);

        final PopupSize popupSize =
                PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT);
        //noinspection unused e
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Initialise New Floor Map")
                .onShow(e -> {
                    getView().focus();
                    validate();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        applyInitialisation(e, taskMonitorFactory);
                    } else {
                        deleteAndAbort(e, taskMonitorFactory);
                    }
                })
                .fire();
    }

    /// Hands off to [#saveInitialisation].
    ///
    /// This used to fetch the chosen store to check its `StateType`, because only a temporal
    /// state store records the effective time the events query selects. That check is gone with the
    /// dedicated document type, which fixes the state type in its constructor — there is no longer a
    /// wrong choice to make here.
    ///
    /// @param e   the hide-popup event to control dialog dismissal; never null
    /// @param tmf task monitor factory for REST calls; never null
    private void applyInitialisation(final HidePopupRequestEvent e,
                                     final TaskMonitorFactory tmf) {
        final DocRef factsDocRef = factsStorePresenter.getSelectedEntityReference();
        final DocRef eventsDocRef = eventsStorePresenter.getSelectedEntityReference();

        // No state-type check: the picker is restricted to FloorMapEventStoreDoc, and that type
        // always carries TEMPORAL_STATE - its constructor sets it regardless of what it is given.
        // The fetch this used to make could only ever have confirmed what the type guarantees.
        saveInitialisation(e, tmf, factsDocRef, eventsDocRef);
    }

    /// Patches the new document with the chosen store references and the default
    /// queries, then saves it.
    ///
    /// The events query written here selects `EffectiveTime`, which every
    /// [FloorMapEventStoreDoc] records: the type fixes its state type, so there is nothing to
    /// confirm first.
    ///
    /// Postcondition: on success, the document has been updated and
    /// `completionCallback` receives `true`.
    ///
    /// @param e             the hide-popup event to control dialog dismissal; never null
    /// @param tmf           task monitor factory for REST calls; never null
    /// @param factsDocRef   the chosen facts store; never null
    /// @param eventsDocRef  the chosen events store; never null
    private void saveInitialisation(final HidePopupRequestEvent e,
                                    final TaskMonitorFactory tmf,
                                    final DocRef factsDocRef,
                                    final DocRef eventsDocRef) {
        // Fetch the doc, patch it, and save
        //noinspection unused error
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

                    //noinspection unused savedDoc, error
                    restFactory
                            .create(FLOOR_MAP_RESOURCE)
                            .method(res2 -> res2.update(updated.getUuid(), updated))
                            .onSuccess(savedDoc -> {
                                e.hide();
                                completionCallback.accept(true);
                            })
                            .onFailure(error -> e.reset())
                            .taskMonitorFactory(tmf)
                            .exec();
                })
                .onFailure(error -> e.reset())
                .taskMonitorFactory(tmf)
                .exec();
    }

    /// Deletes the freshly-created document and signals cancellation.
    ///
    /// The explorer tree is refreshed after deletion so the
    /// now-deleted node disappears from the UI.
    ///
    /// Postcondition: the document has been deleted (or a
    /// best-effort attempt was made) and `completionCallback`
    /// receives `false`.
    ///
    /// @param e   the hide-popup event to control dialog dismissal;
    ///         never null
    /// @param tmf task monitor factory for REST calls; never null
    private void deleteAndAbort(final HidePopupRequestEvent e,
                                final TaskMonitorFactory tmf) {
        e.hide();
        //noinspection unused result, error
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.delete(
                        new ExplorerServiceDeleteRequest(List.of(docRef))))
                .onSuccess(result -> {
                    RefreshExplorerTreeEvent.fire(
                            FloorMapInitPresenter.this);
                    completionCallback.accept(false);
                })
                .onFailure(error -> {
                    // Best effort — still abort
                    completionCallback.accept(false);
                })
                .taskMonitorFactory(tmf)
                .exec();
    }

    /// View interface for the FloorMap initialisation dialog.
    ///
    /// Implementations must provide labelled slots for the
    /// Facts Store and Events Store selection widgets.
    public interface FloorMapInitView extends View, Focus {

        /// Sets the view for the Facts Store selector.
        ///
        /// @param view the DocSelectionBox view for selecting a
        ///         [SqlTemporalStoreDoc]; never null
        void setFactsStoreView(View view);

        /// Sets the view for the Events Store selector.
        ///
        /// @param view the DocSelectionBox view for selecting a
        ///         [FloorMapEventStoreDoc]; never null
        void setEventsStoreView(View view);
    }
}
