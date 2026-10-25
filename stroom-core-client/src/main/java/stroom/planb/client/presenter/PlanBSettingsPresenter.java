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

package stroom.planb.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.planb.client.component.HistogramSettingsEditor;
import stroom.planb.client.component.MetricSettingsEditor;
import stroom.planb.client.component.PlanBTypeSettingsEditor;
import stroom.planb.client.component.RangeStateSettingsEditor;
import stroom.planb.client.component.SessionSettingsEditor;
import stroom.planb.client.component.StateSettingsEditor;
import stroom.planb.client.component.TemporalRangeStateSettingsEditor;
import stroom.planb.client.component.TemporalStateSettingsEditor;
import stroom.planb.client.component.TraceSettingsEditor;
import stroom.planb.client.presenter.PlanBSettingsPresenter.PlanBSettingsView;
import stroom.planb.client.view.GeneralSettingsWidget;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.StateType;
import stroom.widget.component.client.Contexts;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class PlanBSettingsPresenter
        extends DocPresenter<PlanBSettingsView, PlanBDoc>
        implements StateTypeChangeUiHandlers {

    // The settings of the store's type, made again when the type changes
    private PlanBTypeSettingsEditor editor;
    private StateType currentStateType;
    // Read only until its document (or what embeds it) says otherwise
    private boolean readOnly = true;

    // The settings every type has, carried over when the type changes
    private Long maxStoreSize;
    private Boolean synchroniseMerge;
    private Boolean overwrite;
    private DurationSetting condense;
    private RetentionSettings retention;

    private Runnable onChange = () -> {
    };

    @Inject
    public PlanBSettingsPresenter(
            final EventBus eventBus,
            final PlanBSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
        // Read only as its document (or what embeds it) is, now and as that changes
        watch(Contexts.READ_ONLY, this::showReadOnly);
    }

    private void showReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        getView().onReadOnly(readOnly);
        if (editor != null) {
            editor.setReadOnly(readOnly);
        }
    }

    @Override
    public void onStateTypeChange() {
        changeStateType();
    }

    @Override
    protected void onRead(final DocRef docRef, final PlanBDoc doc) {
        currentStateType = null;
        getView().setStateType(doc.getStateType());
        changeStateType();
    }

    @Override
    protected PlanBDoc onWrite(final PlanBDoc doc) {
        AbstractPlanBSettings settings = null;
        if (editor != null) {
            settings = editor.write();
        }
        return doc.copy().stateType(getView().getStateType()).settings(settings).build();
    }

    private void changeStateType() {
        if (editor != null) {
            keepCommonSettings(editor);
        }

        final StateType stateType = getView().getStateType();
        editor = makeEditor(stateType);
        if (editor != null) {
            giveCommonSettings(editor);
            editor.setReadOnly(readOnly);
            if (currentStateType == null && getEntity() != null) {
                editor.read(getEntity().getSettings());
            }
            getView().setSettingsEditor(editor);
            editor.setOnChange(() -> {
                if (getEntity() != null) {
                    onChange();
                }
                // For embedded use (e.g. TracesSettingsPresenter): propagate the
                // change so listeners registered via addChangeHandler are notified.
                onChange.run();
            });
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

    public void setStateTypeLocked(final boolean locked) {
        getView().setStateTypeVisible(!locked);
    }

    /// Shows the settings of a document this is embedded in, rather than reading a document; they
    /// are read only as that document is.
    ///
    /// @param settings  The settings.
    /// @param stateType The type of state they are for.
    public void readSettings(final AbstractPlanBSettings settings,
                             final StateType stateType) {
        getView().setStateType(stateType);
        currentStateType = null;
        changeStateType();
        if (editor != null) {
            editor.read(settings);
        }
    }

    public AbstractPlanBSettings writeSettings() {
        return editor != null
                ? editor.write()
                : null;
    }

    public void setSharedFileStoreLocked(final boolean locked) {
        if (editor != null && editor.getSharedFileStoreSettings() != null) {
            editor.getSharedFileStoreSettings().setSharedFileStoreLocked(locked);
        }
    }

    /// @param onChange What to run when the user changes a setting, for a screen this is embedded in.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    public interface PlanBSettingsView
            extends View, HasUiHandlers<StateTypeChangeUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        StateType getStateType();

        void setStateType(StateType stateType);

        void setStateTypeVisible(boolean visible);

        /// Shows the settings of the store's type.
        ///
        /// @param editor The settings.
        void setSettingsEditor(IsWidget editor);
    }
}
