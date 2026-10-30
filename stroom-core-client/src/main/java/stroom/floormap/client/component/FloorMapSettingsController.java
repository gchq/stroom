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

import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.floormap.client.query.FloorMapQueryBuilder;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.ValueFormat;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The *Settings* tab of a [FloorMapDoc] ([FloorMapSettings]), as a tab of the floor map's
/// document: lets users configure
///
/// - The **Events Store** reference – a [FloorMapEventStoreDoc] read for
///   floor-map event data. Nothing writes to it from here.
/// - The **Facts Store** reference – a [SqlTemporalStoreDoc] used to
///   persist floor-map fact data.
/// - The **Value Format** – the serialisation format for map values
///   (selected from the [ValueFormat] enum).
/// - The **Value Schema** – an editable grid of
///   [FloorMapFieldMapping] entries that define the fields, their roles, paths,
///   display names, and default values.
/// - The two generated reads (the histogram and Show All queries).
///
/// Read only as its document is.
public class FloorMapSettingsController extends Controller {

    private final FloorMapSettings view;
    private final DocSelectionController eventsStore;
    private final DocSelectionController factsStore;

    private FloorMapDoc doc;

    /// Set once the Editor tab enables area support on this document; [#read]
    /// then re-applies [#applyAreaPatch()] so the grid never reverts the
    /// upgrade before it is persisted.
    private boolean areaPatchActive;

    /// Set once the Editor tab enables status support on this document; [#read]
    /// then re-applies [#applyStatusPatch()], as for [#areaPatchActive].
    private boolean statusPatchActive;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the two store pickers.
    @Inject
    public FloorMapSettingsController(final EventBus eventBus,
                                      final FloorMapSettings view,
                                      final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        eventsStore = make(docSelectionProvider);
        eventsStore.attach(view.getEventsStoreBox());
        eventsStore.setCaption("Choose Events Store");
        eventsStore.setIncludedTypes(FloorMapEventStoreDoc.TYPE);
        eventsStore.setRequiredPermissions(DocumentPermission.USE);

        factsStore = make(docSelectionProvider);
        factsStore.attach(view.getFactsStoreBox());
        factsStore.setCaption("Choose Facts Store");
        factsStore.setIncludedTypes(SqlTemporalStoreDoc.TYPE);
        factsStore.setRequiredPermissions(DocumentPermission.USE);

        // No validation on selection: the picker only offers FloorMapEventStoreDoc, and that
        // type fixes its own state type, so there is no longer a wrong store to choose.
        own(eventsStore.addSelectionListener(docRef -> markChanged()));
        own(factsStore.addSelectionListener(docRef -> markChanged()));
        view.setOnChange(this::markChanged);

        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            eventsStore.setEnabled(!readOnly);
            factsStore.setEnabled(!readOnly);
        });
    }

    /// Shows a floor map's settings.
    ///
    /// @param docRef      The document.
    /// @param floorMapDoc Its settings.
    public void read(final DocRef docRef, final FloorMapDoc floorMapDoc) {
        this.doc = floorMapDoc;
        eventsStore.setSelectedEntityReference(floorMapDoc.getEventsStoreRef(), true);
        factsStore.setSelectedEntityReference(floorMapDoc.getFactsStoreRef(), true);

        // Shown with their defaults rather than blank, so the shape a user is editing is visible.
        view.setHistogramQuery(NullSafe.isBlankString(floorMapDoc.getHistogramQuery())
                ? FloorMapQueryBuilder.defaultHistogramQuery()
                : floorMapDoc.getHistogramQuery());
        view.setExtentQuery(NullSafe.isBlankString(floorMapDoc.getExtentQuery())
                ? FloorMapQueryBuilder.defaultExtentQuery()
                : floorMapDoc.getExtentQuery());

        view.setValueFormat(floorMapDoc.getValueFormat());
        view.setMappings(floorMapDoc.getValueSchema());

        // Keep an Editor-tab area-support upgrade visible in the schema grid: the
        // document just read may predate the upgrade (it is only persisted on
        // save), and this tab's write replaces the schema wholesale.
        if (areaPatchActive) {
            applyAreaPatch();
        }
        if (statusPatchActive) {
            applyStatusPatch();
        }

        // Note: measurementUnits is deliberately NOT read or written by this tab.
        // A map's scale is set with the Editor's Set Scale tool and staged in its
        // doc session; because nothing here touches the field, copy() carries it
        // through untouched and no cross-tab patching is needed.
    }

    /// Merges the default area mappings (`GEOMETRY`/`FILL`/
    /// `OPACITY`) into the Value Schema grid, if absent. Called when the
    /// Editor tab enables area support on this document — this tab writes
    /// `valueSchema` wholesale from its grid state on save, so an unpatched
    /// grid would silently revert the upgrade. Idempotent; stays active so
    /// subsequent reads re-apply it until the upgrade is persisted (after which
    /// the merge is a no-op). The matching `"area"` type style is handled
    /// by the Layers panel.
    public void applyAreaPatch() {
        areaPatchActive = true;
        view.replaceMappings(FloorMapFieldMapping.withAreaMappings(view.getMappings(), docValueFormat()));
    }

    /// Merges the default `STATUS` mapping into the Value Schema grid, if
    /// absent. Called when the Editor tab enables status support on this
    /// document, for the same reason as [#applyAreaPatch()]: this tab writes
    /// `valueSchema` wholesale from its grid state on save, so an unpatched
    /// grid would silently revert the upgrade. Idempotent; stays active so
    /// subsequent reads re-apply it until the upgrade is persisted.
    public void applyStatusPatch() {
        statusPatchActive = true;
        view.replaceMappings(FloorMapFieldMapping.withStatusMapping(view.getMappings(), docValueFormat()));
    }

    // The value format of the document read, as the patches' paths are for it
    private ValueFormat docValueFormat() {
        return doc != null
                ? doc.getValueFormat()
                : ValueFormat.JSON;
    }

    /// Writes the settings shown into a copy of a floor map.
    ///
    /// @param doc The document.
    /// @return A copy of it with the settings shown.
    public FloorMapDoc write(final FloorMapDoc doc) {
        // Note: typeStyles is intentionally NOT written here — it is owned by
        // the Layers panel (persisted via the Editor tab). copy() preserves the
        // document's existing typeStyles.
        return doc.copy()
                .eventsStoreRef(eventsStore.getSelectedEntityReference())
                .factsStoreRef(factsStore.getSelectedEntityReference())
                .valueFormat(view.getValueFormat())
                .valueSchema(view.getMappings())
                .histogramQuery(view.getHistogramQuery())
                .extentQuery(view.getExtentQuery())
                .build();
    }
}
