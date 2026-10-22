/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.Button;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.presenter.client.ViewImpl;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.view.LayerContainerImpl;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.function.Consumer;

public class AskStroomAiConfigViewImpl
        extends ViewImpl
        implements AskStroomAiConfigView {

    private final Widget widget;

    @UiField
    TabBar tabBar;
    @UiField
    LayerContainerImpl layerContainer;
    @UiField
    Button restoreFromDefaults;
    @UiField
    Button setDefaults;

    private Consumer<TaskMonitorFactory> onRestoreFromDefaultsHandler;
    private Consumer<TaskMonitorFactory> onSetDefaultsHandler;

    @Inject
    public AskStroomAiConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        tabBar.setTabPanel(layerContainer.getElement());
        restoreFromDefaults.setVisible(true);
        setDefaults.setVisible(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    @Override
    public void setRestoreFromDefaults(final Consumer<TaskMonitorFactory> handler) {
        this.onRestoreFromDefaultsHandler = handler;
    }

    @Override
    public void allowSetDefaults(final boolean allow) {
        setDefaults.setVisible(allow);
    }

    @Override
    public void setOnSetDefaults(final Consumer<TaskMonitorFactory> handler) {
        this.onSetDefaultsHandler = handler;
    }

    @UiHandler("restoreFromDefaults")
    public void onRestoreFromDefaultsClick(final ClickEvent event) {
        if (onRestoreFromDefaultsHandler != null) {
            onRestoreFromDefaultsHandler.accept(restoreFromDefaults);
        }
    }

    @UiHandler("setDefaults")
    public void onSetDefaultsClick(final ClickEvent event) {
        if (onSetDefaultsHandler != null) {
            onSetDefaultsHandler.accept(setDefaults);
        }
    }

    // ---------------------------------------------------------------------

    public interface Binder extends UiBinder<Widget, AskStroomAiConfigViewImpl> {

    }
}
