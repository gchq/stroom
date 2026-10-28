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

import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.floormap.client.presenter.FloorMapEventQuery;
import stroom.floormap.shared.FloorMapDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// A floor map's editor: its Map, Editor, Events Query, Settings, Assets, Documentation and
/// Permissions tabs (see [DocumentEditor]), and what passes between them.
///
/// It coordinates the save across them: the Editor tab's staged fact changes and the assets aren't
/// in the document, so they make it dirty too, and [#getPostSaveCallback()] flushes the Editor's
/// changes and then saves the assets, so a single save persists both.
public class FloorMapDocEditor extends DocumentEditor<FloorMapDoc> {

    private static final TabData MAP = new TabDataImpl("Map");
    private static final TabData EDITOR = new TabDataImpl("Editor");
    private static final TabData EVENTS_QUERY = new TabDataImpl("Events Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData ASSETS = new TabDataImpl("Assets");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DocumentAssetsTabController<FloorMapDoc> assetsTab;
    private FloorMapMapController map;
    private FloorMapEditorController editor;
    private FloorMapSettingsController settings;

    /// The tab that was showing before the most recent switch, to pause the timeline when the user
    /// leaves the Map or Editor tab.
    private TabData previousTab;

    /// Set when the Editor tab enables area support on this document, so a Settings tab made
    /// *afterwards* still gets its grid patched (see [FloorMapSettingsController#applyAreaPatch()]).
    private boolean areaSupportEnabled;

    /// Set when the Editor tab enables status support on this document, so a Settings tab made
    /// *afterwards* still gets its grid patched (see
    /// [FloorMapSettingsController#applyStatusPatch()]).
    private boolean statusSupportEnabled;

    /// The initial view `{scale, offsetX, offsetY}` the Map tab zoomed to fit on open, stashed so
    /// the Editor tab (opened afterwards) adopts the same zoom and translation and nothing jumps on
    /// the switch. `null` until the Map has fitted, or when the Editor is opened first (it fits
    /// locally).
    private double[] sharedInitialView;

    /// Makes the editor, with its tabs.
    @Inject
    public FloorMapDocEditor(final EventBus eventBus,
                             final LinkTabPanel view,
                             final Provider<FloorMapMapController> mapProvider,
                             final Provider<FloorMapEditorController> editorProvider,
                             final Provider<FloorMapSettingsController> settingsProvider,
                             final Provider<MarkdownEditController> markdownEditProvider,
                             final Provider<FloorMapQueryController> queryProvider,
                             final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                             final DocumentAssetsTabController<FloorMapDoc> assetsTab) {
        super(eventBus, view);
        this.assetsTab = own(assetsTab);

        addTab(MAP,
                () -> {
                    map = mapProvider.get();
                    // Its events' types surface as provisional layers in the Editor's Layers panel
                    map.setOnEventData(objects -> {
                        if (editor != null) {
                            editor.addEventTypes(objects);
                        }
                    });
                    // Stash the Map's zoom-to-fit initial view so the Editor tab adopts the same
                    // zoom and translation (no jump on the first switch)
                    map.setInitialViewListener(initialView -> {
                        sharedInitialView = initialView;
                        if (editor != null) {
                            editor.setInitialViewState(initialView);
                        }
                    });
                    return map;
                },
                FloorMapMapController::read,
                FloorMapMapController::write);

        addTab(EDITOR,
                () -> {
                    editor = editorProvider.get();
                    // Its staged fact edits aren't in the written document: the document asks it
                    // for them (hasAssociatedDirty), so it is told of each change directly
                    editor.setOnChange(this::onChange);
                    // Adopt the Map tab's initial zoom and translation, if it has fitted already,
                    // so the Editor opens on the same view. If the Editor is opened first this is
                    // null and it fits its own content locally.
                    if (sharedInitialView != null) {
                        editor.setInitialViewState(sharedInitialView);
                    }
                    // When the Editor enables area support (schema/type-style upgrade), the
                    // Settings tab's grid must be patched again or its wholesale write would
                    // silently revert the upgrade on save
                    editor.setAreaSupportEnabledListener(() -> {
                        areaSupportEnabled = true;
                        if (settings != null) {
                            settings.applyAreaPatch();
                        }
                    });
                    // Likewise when the Editor enables status support (a Status mapping, so facts
                    // can be deleted from a point in time)
                    editor.setStatusSupportEnabledListener(() -> {
                        statusSupportEnabled = true;
                        if (settings != null) {
                            settings.applyStatusPatch();
                        }
                    });
                    // Type styles are owned by the Editor's Layers panel and written by the Editor
                    // itself. The Settings tab doesn't manage them, so no cross-tab sync is needed.
                    return editor;
                },
                FloorMapEditorController::read,
                FloorMapEditorController::write);

        addTab(EVENTS_QUERY,
                () -> {
                    final FloorMapQueryController query = queryProvider.get();
                    // Only an edit on this tab (its query, or its column dropdowns) marks the floor
                    // map dirty
                    query.setOnChange(this::onChange);
                    // Its results are drawn on the Map tab, and their types surface as provisional
                    // layers in the Editor's Layers panel
                    query.setOnEventData(objects -> {
                        if (map != null) {
                            map.showQueryEvents(objects);
                        }
                        if (editor != null) {
                            editor.addEventTypes(objects);
                        }
                    });
                    return query;
                },
                (query, docRef, doc) -> query.read(docRef,
                        doc.getEventsQuery(),
                        doc.getEventsQueryTimeRange(),
                        doc.getEventsQueryTablePreferences(),
                        doc.getEventColumns(),
                        true,
                        FloorMapEventQuery.buildQueryVariables(doc)),
                (query, doc) -> doc.copy()
                        .eventsQuery(query.getQuery())
                        .eventsQueryTimeRange(query.getQueryTimeRange())
                        .eventsQueryTablePreferences(query.getQueryTablePreferences())
                        .eventColumns(query.getEventColumns())
                        .build());

        addTab(SETTINGS,
                () -> {
                    settings = settingsProvider.get();
                    if (areaSupportEnabled) {
                        // Area support was enabled before this tab was first opened: keep its
                        // grid patched (idempotent; also applied again on read)
                        settings.applyAreaPatch();
                    }
                    if (statusSupportEnabled) {
                        settings.applyStatusPatch();
                    }
                    return settings;
                },
                FloorMapSettingsController::read,
                FloorMapSettingsController::write);
        // The assets are saved after the document (getPostSaveCallback), so it writes nothing
        addOwnedTab(ASSETS, assetsTab, DocumentAssetsTabController::read);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                FloorMapDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(MAP);
    }

    /// Reacts to this document's tab being shown or hidden.
    ///
    /// Without this, a switch to another Stroom document left whichever timeline was playing still
    /// playing, its result stores churning on a document nobody was looking at, and on return gave
    /// the Map no chance to catch up on facts or events that had arrived meanwhile. The query and
    /// dashboard editors, which also run searches, do the same.
    ///
    /// Both branches act on the inner tab actually showing, rather than on the Map
    /// unconditionally: two tabs carry a timeline, so hiding must pause whichever one is showing,
    /// and refreshing a Map that isn't the visible inner tab would issue reads for something the
    /// user can't see. This mirrors [#afterSelectTab(TabData)].
    ///
    /// @param visible Whether the document's tab is showing.
    @Override
    public void onContentTabVisible(final boolean visible) {
        if (MAP.equals(previousTab)) {
            map.onContentTabVisible(visible);
        } else if (EDITOR.equals(previousTab) && !visible) {
            // The Editor has a timeline to pause but no held state to catch up, so it is
            // deliberately one-sided, as afterSelectTab is
            editor.pauseTimeline();
        }
    }

    /// Pauses the Map's or Editor's timeline when the user leaves its tab, catches the Map up when
    /// they return to it, and checks the assets for changes when their tab is shown.
    ///
    /// @param tab The tab now showing.
    @Override
    protected void afterSelectTab(final TabData tab) {
        // Pause whichever timeline-bearing tab the user just left
        if (!tab.equals(previousTab)) {
            if (MAP.equals(previousTab)) {
                // Not just pauseTimeline(): leaving the Map for a sibling tab hides it just as
                // surely as leaving the document does, and its facts cadence should stop either way
                map.onContentTabVisible(false);
            } else if (EDITOR.equals(previousTab)) {
                editor.pauseTimeline();
            }

            // Returning to the Map tab reads facts again so edits saved from the Editor tab (moved
            // objects, new icons and backgrounds) are visible, and restarts the cadence the branch
            // above stopped
            if (MAP.equals(tab) && previousTab != null) {
                map.onContentTabVisible(true);
            }

            previousTab = tab;
        }

        if (ASSETS.equals(tab)) {
            onChange();
        }
    }

    /// @return Whether the Map, the Editor's staged changes or the assets have changes that aren't
    /// saved.
    @Override
    protected boolean hasAssociatedDirty() {
        return (map != null && map.hasAssociatedDirty())
               || (editor != null && editor.hasPendingChanges())
               || assetsTab.isDirty();
    }

    /// @return The Editor tab's flush of its staged changes, then the save of the assets, run once
    /// the document is saved, so a single save persists both.
    @Override
    public BiConsumer<FloorMapDoc, Consumer<FloorMapDoc>> getPostSaveCallback() {
        return this::flushEditorThenSaveAssets;
    }

    /// @return The save of the assets to the copy, run once a copy of the document is saved.
    @Override
    public BiConsumer<FloorMapDoc, Consumer<FloorMapDoc>> getPostSaveAsCallback() {
        return assetsTab::onSaveAs;
    }

    // Flushes the Editor's staged changes, if any, and only once that succeeds saves the assets
    private void flushEditorThenSaveAssets(final FloorMapDoc document, final Consumer<FloorMapDoc> callback) {
        if (editor != null && editor.hasPendingChanges()) {
            editor.onSave(document, doc -> {
                // The flush has landed in the temporal store: refresh the Map tab so it picks up
                // the newly persisted facts (it might be the visible tab, e.g. when saving with
                // the keyboard shortcut)
                if (map != null) {
                    map.refresh();
                }
                assetsTab.onSave(doc, callback);
            });
        } else {
            assetsTab.onSave(document, callback);
        }
    }
}
