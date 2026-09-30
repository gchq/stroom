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

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.docref.DocRef;
import stroom.document.asset.client.presenter.DocumentAssetPresenter;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.floormap.shared.FloorMapDoc;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Provider;

/// Top-level document tab presenter for a [FloorMapDoc].
///
/// Hosts all sub-tabs — Map, Editor, Events Query, Settings,
/// Assets, Documentation, and Permissions — and coordinates the save chain
/// across them.  The [#getPostSaveCallback()] method chains the Editor
/// tab’s pending-change flush with the asset save so that both are persisted
/// in a single user-initiated save.
public class FloorMapPresenter extends DocTabPresenter<LinkTabPanelView, FloorMapDoc> {

    private static final TabData MAP = new TabDataImpl("Map");
    private static final TabData EDITOR = new TabDataImpl("Editor");
    private static final TabData EVENTS_QUERY = new TabDataImpl("Events Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData ASSETS = new TabDataImpl("Assets");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DocumentAssetPresenter<FloorMapDoc> documentAssetPresenter;
    private FloorMapMapPresenter floorMapMapPresenter;
    private FloorMapEditorPresenter floorMapEditorPresenter;
    private FloorMapSettingsPresenter floorMapSettingsPresenter;
    private FloorMapQueryPresenter eventsQueryPresenter;

    /// Tracks the presenter that was active before the most recent tab switch.
    /// Used to pause the timeline when the user navigates away from the Map or
    /// Editor tab.
    private PresenterWidget<?> previousContent;

    /// Set when the Editor tab enables area support on this document, so a
    /// Settings tab created *afterwards* still gets its grid patched
    /// (see [FloorMapSettingsPresenter#applyAreaPatch()]).
    private boolean areaSupportEnabled;

    /// The initial view `{scale, offsetX, offsetY}` the Map tab zoomed to
    /// fit on open, stashed so the Editor tab (opened afterwards) adopts the same
    /// zoom + translation and nothing jumps on the switch. `null` until the
    /// Map has fitted, or when the Editor is opened first (it fits locally).
    private double[] sharedInitialView;

    @Inject
    public FloorMapPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<FloorMapMapPresenter> floorMapMapPresenterProvider,
                             final Provider<FloorMapEditorPresenter> floorMapEditorPresenterProvider,
                             final Provider<FloorMapSettingsPresenter> floorMapSettingsPresenterProvider,
                             final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                             final Provider<FloorMapQueryPresenter> floorMapQueryPresenterProvider,
                             final DocumentUserPermissionsTabProvider<FloorMapDoc> documentUserPermissionsTabProvider,
                             final DocumentAssetPresenter<FloorMapDoc> documentAssetPresenter) {
        super(eventBus, view);
        this.documentAssetPresenter = documentAssetPresenter;

        addTab(MAP, new DocTabProvider<>(() -> {
            floorMapMapPresenter = floorMapMapPresenterProvider.get();
            // Stash the Map's zoom-to-fit initial view so the Editor tab adopts
            // the same zoom + translation (no jump on the first switch).
            floorMapMapPresenter.setInitialViewListener(initialView -> {
                sharedInitialView = initialView;
                if (floorMapEditorPresenter != null) {
                    floorMapEditorPresenter.setInitialViewState(initialView);
                }
            });
            return floorMapMapPresenter;
        }));

        addTab(EDITOR, new DocTabProvider<>(() -> {
            floorMapEditorPresenter = floorMapEditorPresenterProvider.get();
            // Adopt the Map tab's initial zoom + translation, if it has fitted
            // already, so the Editor opens on the same view. If the Editor is
            // opened first this is null and it fits its own content locally.
            if (sharedInitialView != null) {
                floorMapEditorPresenter.setInitialViewState(sharedInitialView);
            }
            // When the Editor enables area support (schema/type-style upgrade),
            // the Settings tab's grid must be re-patched or its wholesale
            // onWrite would silently revert the upgrade on save.
            floorMapEditorPresenter.setAreaSupportEnabledListener(() -> {
                areaSupportEnabled = true;
                if (floorMapSettingsPresenter != null) {
                    floorMapSettingsPresenter.applyAreaPatch();
                }
            });
            // Type styles are owned by the Editor's Layers panel and persisted
            // via the Editor's own onWrite. The Settings tab does not manage them,
            // so no cross-tab sync is needed.
            return floorMapEditorPresenter;
        }));

        addTab(EVENTS_QUERY, new AbstractTabProvider<FloorMapDoc, FloorMapQueryPresenter>(eventBus) {
            @Override
            protected FloorMapQueryPresenter createPresenter() {
                eventsQueryPresenter = floorMapQueryPresenterProvider.get();
                // Scope this to the tab's own query editor. A bus-level
                // addHandler(ChangeEvent.getType(), ...) hears ChangeEvent from the whole
                // application — theme and editor preferences, dictionary lists, expression
                // editors, query result tables, some of them per keystroke — so it marked
                // this floor map dirty, and triggered a full document write-diff, whenever
                // anything anywhere changed. addChangeHandler delegates to
                // QueryEditPresenter.addHandlerToSource, so only an edit here fires it.
                registerHandler(eventsQueryPresenter.addChangeHandler(() -> fireDirtyEvent(true)));
                return eventsQueryPresenter;
            }

            @Override
            public void onRead(final FloorMapQueryPresenter presenter,
                               final DocRef docRef,
                               final FloorMapDoc document,
                               final boolean readOnly) {
                presenter.read(docRef, document.getEventsQuery(), document.getEventsQueryTimeRange(),
                        document.getEventsQueryTablePreferences(), document.getEventColumns(), true,
                        FloorMapQueryPresenter.buildQueryVariables(document));
                presenter.setTaskMonitorFactory(FloorMapPresenter.this);
            }

            @Override
            public FloorMapDoc onWrite(final FloorMapQueryPresenter presenter,
                                       final FloorMapDoc document) {
                return document.copy()
                        .eventsQuery(presenter.getQuery())
                        .eventsQueryTimeRange(presenter.getQueryTimeRange())
                        .eventsQueryTablePreferences(presenter.getQueryTablePreferences())
                        .eventColumns(presenter.getEventColumns())
                        .build();
            }
        });

        addTab(SETTINGS, new DocTabProvider<>(() -> {
            floorMapSettingsPresenter = floorMapSettingsPresenterProvider.get();
            if (areaSupportEnabled) {
                // Area support was enabled before this tab was first opened —
                // keep its grid patched (idempotent; also re-applied on read).
                floorMapSettingsPresenter.applyAreaPatch();
            }
            return floorMapSettingsPresenter;
        }));
        addTab(ASSETS, new DocTabProvider<>(() -> documentAssetPresenter));

        addTab(DOCUMENTATION, new MarkdownTabProvider<>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final FloorMapDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public FloorMapDoc onWrite(final MarkdownEditPresenter presenter,
                                       final FloorMapDoc document) {
                return document.copy().description(presenter.getText()).build();
            }
        });

        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(MAP);
    }

    /// {@inheritDoc}
    @Override
    protected void onRead(final DocRef docRef, final FloorMapDoc document, final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
    }

    /// Reacts to this *document's* content tab being fronted or backgrounded.
    ///
    /// [#afterSelectTab] covers only this document's inner tabs, so without this a switch
    /// to a different Stroom document left whichever timeline was playing still playing, its result
    /// stores churning on a document nobody was looking at — and, on return, gave the Map no chance
    /// to catch up on facts or events that had arrived meanwhile.
    ///
    /// `QueryDocPresenter` and `DashboardSuperPresenter` — the other two document
    /// types that run searches — consume this event in exactly this shape.
    ///
    /// Both branches act on [#previousContent], the inner tab actually on screen, rather
    /// than on the Map unconditionally. Two tabs carry a timeline, so hiding must pause whichever
    /// one is showing; and refreshing a Map that is not the visible inner tab would issue reads for
    /// something the user cannot see. This mirrors `afterSelectTab`'s own dispatch.
    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e ->
                onContentTabVisible(e.getTabData() == this)));
    }

    private void onContentTabVisible(final boolean visible) {
        if (previousContent == floorMapMapPresenter && floorMapMapPresenter != null) {
            floorMapMapPresenter.onContentTabVisible(visible);
        } else if (previousContent == floorMapEditorPresenter && floorMapEditorPresenter != null
                   && !visible) {
            // The Editor has a timeline to pause but no held state to catch up, so it is
            // deliberately one-sided - the same asymmetry afterSelectTab has.
            floorMapEditorPresenter.pauseTimeline();
        }
    }

    /// Performs post-tab-selection logic: pauses the Map / Editor timeline when
    /// the user navigates away from those tabs, and triggers asset change
    /// detection.
    @Override
    protected void afterSelectTab(final PresenterWidget<?> content) {
        // Pause whichever timeline-bearing tab the user just left.
        if (previousContent != content) {
            if (previousContent == floorMapMapPresenter && floorMapMapPresenter != null) {
                // Not just pauseTimeline(): leaving the Map for a sibling tab hides it just as
                // surely as leaving the document does, and its facts cadence should stop either way.
                floorMapMapPresenter.onContentTabVisible(false);
            } else if (previousContent == floorMapEditorPresenter && floorMapEditorPresenter != null) {
                floorMapEditorPresenter.pauseTimeline();
            }

            // Returning to the Map tab re-reads facts so edits saved from the
            // Editor tab (moved objects, new icons/backgrounds) are visible, and
            // restarts the cadence the branch above stopped.
            if (content == floorMapMapPresenter && floorMapMapPresenter != null
                    && previousContent != null) {
                floorMapMapPresenter.onContentTabVisible(true);
            }

            previousContent = content;
        }

        if (content == documentAssetPresenter) {
            onChange();
        }
    }

    @Override
    public String getType() {
        return FloorMapDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    /// Returns `true` when any associated presenter (Map, Editor, or
    /// Assets) has unsaved changes.
    @Override
    protected boolean hasAssociatedDirty() {
        return super.hasAssociatedDirty() ||
                (floorMapMapPresenter != null && floorMapMapPresenter.hasAssociatedDirty()) ||
                (floorMapEditorPresenter != null && floorMapEditorPresenter.hasPendingChanges()) ||
                (documentAssetPresenter != null && documentAssetPresenter.isDirty());
    }

    /// Returns the post-save callback for the Editor tab's staged-change flush
    /// followed by the document asset save.
    ///
    /// The returned [java.util.function.BiConsumer] first flushes any
    /// pending Editor-tab changes via
    /// [FloorMapEditorPresenter#onSave(FloorMapDoc, Consumer)], then on
    /// success delegates to [stroom.document.asset.client.presenter.DocumentAssetPresenter#onSave]
    /// to persist any associated binary assets.
    ///
    /// @return a BiConsumer that is inserted into the Stroom document save chain
    @Override
    public BiConsumer<FloorMapDoc, Consumer<FloorMapDoc>> getPostSaveCallback() {
        return this::flushEditorThenSaveAssets;
    }

    /// Chains the Editor pending-changes flush and the asset save.
    /// If the editor has pending changes they are flushed first; only on
    /// success does the asset save proceed.
    private void flushEditorThenSaveAssets(final FloorMapDoc document,
                                            final Consumer<FloorMapDoc> callback) {
        if (floorMapEditorPresenter != null && floorMapEditorPresenter.hasPendingChanges()) {
            floorMapEditorPresenter.onSave(document, doc -> {
                // The flush has landed in the temporal store — refresh the Map
                // tab so it picks up the newly-persisted facts (it might be the
                // visible tab, e.g. when saving via the keyboard shortcut).
                if (floorMapMapPresenter != null) {
                    floorMapMapPresenter.refresh();
                }
                documentAssetPresenter.onSave(doc, callback);
            });
        } else {
            documentAssetPresenter.onSave(document, callback);
        }
    }

    /// Provide a callback to be inserted into the SaveAs chain after the document saveAs
    /// has happened.
    ///
    /// @return The consumer for the callback. The second parameter will be the
    ///         consumer to call after this method has completed.
    @Override
    public BiConsumer<FloorMapDoc, Consumer<FloorMapDoc>> getPostSaveAsCallback() {
        return this::saveAsAssets;
    }

    /// Called by DocumentPlugin to do a SaveAs to a new document.
    /// Specified in getPostSaveAsCallback().
    ///
    /// @param document The new document to save to.
    /// @param callback Thing to call when the assets have been saved.
    public void saveAsAssets(final FloorMapDoc document, final Consumer<FloorMapDoc> callback) {
        documentAssetPresenter.onSaveAs(document, callback);
    }
}
