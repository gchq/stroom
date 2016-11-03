/*
 * Copyright 2016 Crown Copyright
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

package stroom.visualisation.client.presenter;

import stroom.app.client.event.DirtyKeyDownHander;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.entity.client.presenter.EntitySettingsPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.explorer.shared.ExplorerData;
import stroom.script.shared.Script;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsUtil;
import stroom.visualisation.shared.Visualisation;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class VisualisationSettingsPresenter
        extends EntitySettingsPresenter<VisualisationSettingsPresenter.VisualisationSettingsView, Visualisation> {
    private final EntityDropDownPresenter scriptPresenter;

    @Inject
    public VisualisationSettingsPresenter(final EventBus eventBus, final VisualisationSettingsView view,
                                          final EntityDropDownPresenter scriptPresenter, final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.scriptPresenter = scriptPresenter;

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        scriptPresenter.setIncludedTypes(Script.ENTITY_TYPE);
        scriptPresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));
        registerHandler(view.getFunctionName().addKeyDownHandler(keyDownHander));
        registerHandler(view.getSettings().addKeyDownHandler(keyDownHander));
        view.setScriptView(scriptPresenter.getView());
    }

    @Override
    public String getType() {
        return Visualisation.ENTITY_TYPE;
    }

    @Override
    protected void onBind() {
        registerHandler(scriptPresenter.addDataSelectionHandler(new DataSelectionHandler<ExplorerData>() {
            @Override
            public void onSelection(final DataSelectionEvent<ExplorerData> event) {
                if (!EqualsUtil.isEquals(scriptPresenter.getSelectedEntityReference(),
                        getEntity().getScriptRef())) {
                    setDirty(true);
                }
            }
        }));
    }

    @Override
    protected void onRead(final Visualisation visualisation) {
        getView().getDescription().setText(visualisation.getDescription());
        getView().getFunctionName().setText(visualisation.getFunctionName());
        getView().getSettings().setText(visualisation.getSettings());
        scriptPresenter.setSelectedEntityReference(visualisation.getScriptRef());
    }

    @Override
    protected void onWrite(final Visualisation visualisation) {
        visualisation.setDescription(getView().getDescription().getText().trim());
        visualisation.setFunctionName(getView().getFunctionName().getText().trim());
        visualisation.setSettings(getView().getSettings().getText().trim());
        visualisation.setScriptRef(scriptPresenter.getSelectedEntityReference());
    }

    public interface VisualisationSettingsView extends View {
        TextArea getDescription();

        TextBox getFunctionName();

        void setScriptView(View view);

        TextArea getSettings();
    }
}
