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

package stroom.dashboard.client.main;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.svg.client.Icon;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public abstract class AbstractComponentPresenter<V extends View> extends MyPresenterWidget<V>
        implements Component, HasDirtyHandlers {
    private final Provider<?> settingsPresenterProvider;
    private TabLayout tabLayout;
    private Components components;
    private ComponentConfig componentData;
    private TabConfig tabConfig;
    private SettingsPresenter settingsPresenter;

    public AbstractComponentPresenter(final EventBus eventBus, final V view,
                                      final Provider<?> settingsPresenterProvider) {
        super(eventBus, view);
        this.settingsPresenterProvider = settingsPresenterProvider;
    }

    @Override
    public Components getComponents() {
        return components;
    }

    /**
     * Called just after a component is created from the component registry.
     *
     * @param components
     */
    @Override
    public void setComponents(final Components components) {
        this.components = components;
    }

    @Override
    public void read(final ComponentConfig componentData) {
        this.componentData = componentData;
    }

    @Override
    public void write(final ComponentConfig componentData) {
        componentData.setType(this.componentData.getType());
        componentData.setId(this.componentData.getId());
        componentData.setName(this.componentData.getName());
        componentData.setSettings(this.componentData.getSettings());
        if (settingsPresenter != null) {
            settingsPresenter.write(componentData);
        }
    }

    @Override
    public ComponentConfig getComponentData() {
        return componentData;
    }

    @Override
    public void showSettings() {
        if (settingsPresenter == null) {
            settingsPresenter = (SettingsPresenter) settingsPresenterProvider.get();
        }

        final PopupUiHandlers uiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    if (settingsPresenter.validate()) {
                        final boolean dirty = settingsPresenter.isDirty(componentData);
                        settingsPresenter.write(componentData);

                        if (dirty) {
                            changeSettings();
                        }

                        HidePopupEvent.fire(AbstractComponentPresenter.this, settingsPresenter);
                    }
                } else {
                    HidePopupEvent.fire(AbstractComponentPresenter.this, settingsPresenter);
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        settingsPresenter.setComponents(components);
        settingsPresenter.read(componentData);

        final PopupSize popupSize = new PopupSize(400, 300, true);
        ShowPopupEvent.fire(this, settingsPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Settings", uiHandlers);
    }

    protected void changeSettings() {
        if (tabLayout != null) {
            tabLayout.refresh();
        }

        setDirty(true);
    }

    @Override
    public void setTabLayout(final TabLayout tabLayout) {
        this.tabLayout = tabLayout;
    }

    public void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public void onRemove() {
        unbind();
    }

    @Override
    public String getId() {
        return componentData.getId();
    }

    @Override
    public String getDisplayValue() {
        return componentData.getName() + " (" + componentData.getId() + ")";
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public TabConfig getTabConfig() {
        return tabConfig;
    }

    @Override
    public void setTabConfig(final TabConfig tabConfig) {
        this.tabConfig = tabConfig;
    }

    /***************
     * Start TabData
     ***************/
    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getLabel() {
        return componentData.getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
    /***************
     * End TabData
     ***************/
}
