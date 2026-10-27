/*
 * Copyright 2024 Crown Copyright
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

package stroom.planb.client.component;

import stroom.docref.DocRef;
import stroom.planb.client.view.GeneralSettingsWidget;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.StateType;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A Plan B store's Settings tab ([PlanBSettings]): its type of state and that type's settings.
/// Changing the type shows the new type's settings afresh, carrying over the settings every type
/// has. Also a Traces store's Settings tab, its type locked to [StateType#TRACE]
/// ([#setStateTypeLocked(boolean)], [#readSettings], [#writeSettings()]). Read only as its
/// document is.
public class PlanBSettingsController extends Controller {

    private final PlanBSettings view;

    // The settings of the store's type, made again when the type changes
    private PlanBTypeSettingsEditor editor;
    private StateType currentStateType;
    // Read only until its document (or what embeds it) says otherwise
    private boolean readOnly = true;
    // The document read, or null when only its settings are shown (a Traces store's)
    private PlanBDoc doc;

    // The settings every type has, carried over when the type changes
    private Long maxStoreSize;
    private Boolean synchroniseMerge;
    private Boolean overwrite;
    private DurationSetting condense;
    private RetentionSettings retention;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public PlanBSettingsController(final EventBus eventBus,
                                   final PlanBSettings view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnStateTypeChange(() -> {
            changeStateType();
            markChanged();
        });
        // Read only as its document (or what embeds it) is, now and as that changes
        watch(Contexts.READ_ONLY, this::showReadOnly);
    }

    private void showReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        view.setReadOnly(readOnly);
        if (editor != null) {
            editor.setReadOnly(readOnly);
        }
    }

    /// Shows a Plan B store's settings.
    ///
    /// @param docRef The store.
    /// @param doc    The store.
    public void read(final DocRef docRef, final PlanBDoc doc) {
        this.doc = doc;
        currentStateType = null;
        view.setStateType(doc.getStateType());
        changeStateType();
    }

    /// Writes the settings shown into a copy of a Plan B store.
    ///
    /// @param doc The store.
    /// @return A copy of it with the settings shown.
    public PlanBDoc write(final PlanBDoc doc) {
        AbstractPlanBSettings settings = null;
        if (editor != null) {
            settings = editor.write();
        }
        return doc.copy().stateType(view.getStateType()).settings(settings).build();
    }

    private void changeStateType() {
        if (editor != null) {
            keepCommonSettings(editor);
        }

        final StateType stateType = view.getStateType();
        editor = makeEditor(stateType);
        if (editor != null) {
            giveCommonSettings(editor);
            editor.setReadOnly(readOnly);
            if (currentStateType == null && doc != null) {
                editor.read(doc.getSettings());
            }
            view.setSettingsEditor(editor);
            editor.setOnChange(this::markChanged);
        }
        currentStateType = stateType;
    }

    // The settings of a type of store
    private static PlanBTypeSettingsEditor makeEditor(final StateType stateType) {
        switch (stateType) {
            case STATE:
                return new StateSettingsEditor();
            case TEMPORAL_STATE:
                return new TemporalStateSettingsEditor();
            case RANGED_STATE:
                return new RangeStateSettingsEditor();
            case TEMPORAL_RANGED_STATE:
                return new TemporalRangeStateSettingsEditor();
            case SESSION:
                return new SessionSettingsEditor();
            case HISTOGRAM:
                return new HistogramSettingsEditor();
            case METRIC:
                return new MetricSettingsEditor();
            case TRACE:
                return new TraceSettingsEditor();
            default:
                return null;
        }
    }

    // Keeps the settings every type has (and those only some have), from the type being left
    private void keepCommonSettings(final PlanBTypeSettingsEditor previous) {
        final GeneralSettingsWidget general = previous.getGeneralSettings();
        maxStoreSize = general.getMaxStoreSize();
        if (previous.hasWriteOptions()) {
            synchroniseMerge = general.getSynchroniseMerge();
            overwrite = general.getOverwrite();
        }
        if (previous.getCondenseSettings() != null) {
            condense = previous.getCondenseSettings().getCondense();
        }
        retention = previous.getRetentionSettings().getRetention();
    }

    // Gives the settings kept to the type being shown, as far as it has them
    private void giveCommonSettings(final PlanBTypeSettingsEditor next) {
        final GeneralSettingsWidget general = next.getGeneralSettings();
        general.setMaxStoreSize(maxStoreSize);
        if (next.hasWriteOptions()) {
            general.setSynchroniseMerge(synchroniseMerge);
            general.setOverwrite(overwrite);
        }
        if (next.getCondenseSettings() != null) {
            next.getCondenseSettings().setCondense(condense);
        }
        next.getRetentionSettings().setRetention(retention);
    }

    /// @param locked Whether the type of state is fixed, so not shown, e.g. for a Traces store.
    public void setStateTypeLocked(final boolean locked) {
        view.setStateTypeVisible(!locked);
    }

    /// Shows the settings of a document that isn't a Plan B store (a Traces store), rather than
    /// reading a document; they are read only as that document is.
    ///
    /// @param settings  The settings.
    /// @param stateType The type of state they are for.
    public void readSettings(final AbstractPlanBSettings settings,
                             final StateType stateType) {
        view.setStateType(stateType);
        currentStateType = null;
        changeStateType();
        if (editor != null) {
            editor.read(settings);
        }
    }

    /// @return The settings shown, or `null` for a type with none.
    public AbstractPlanBSettings writeSettings() {
        return editor != null
                ? editor.write()
                : null;
    }

    /// @param locked Whether the shared file store settings can't be changed, as the store has data
    ///               in it.
    public void setSharedFileStoreLocked(final boolean locked) {
        if (editor != null && editor.getSharedFileStoreSettings() != null) {
            editor.getSharedFileStoreSettings().setSharedFileStoreLocked(locked);
        }
    }
}
