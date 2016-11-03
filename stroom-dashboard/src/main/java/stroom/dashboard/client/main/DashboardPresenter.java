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

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.dashboard.client.flexlayout.FlexLayoutChangeHandler;
import stroom.dashboard.client.flexlayout.PositionAndSize;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.shared.*;
import stroom.dashboard.shared.DashboardConfig.TabVisibility;
import stroom.dashboard.shared.SplitLayoutConfig.Direction;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import java.util.ArrayList;
import java.util.List;

public class DashboardPresenter extends EntityEditTabPresenter<LinkTabPanelView, Dashboard>
        implements FlexLayoutChangeHandler {
    private static final TabData TAB = new TabDataImpl("");
    private final DashboardLayoutPresenter layoutPresenter;
    private final Provider<ComponentAddPresenter> addPresenterProvider;
    private final Components components;
    private final GlyphButtonView addButton;
    private boolean loaded;

    @Inject
    public DashboardPresenter(final EventBus eventBus, final LinkTabPanelView view,
                              final DashboardLayoutPresenter layoutPresenter,
                              final Provider<ComponentAddPresenter> addPresenterProvider, final Components components,
                              final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.layoutPresenter = layoutPresenter;
        this.addPresenterProvider = addPresenterProvider;
        this.components = components;

        layoutPresenter.setFlexLayoutChangeHandler(this);
        layoutPresenter.setComponents(components);

        addButton = addButtonLeft(GlyphIcons.ADD);
        addButton.setTitle("Add Component");
        addButton.setEnabled(false);

        addTab(TAB);
        selectTab(TAB);
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();

        // Remove all components. This should have been done already in the
        // onClose() method.
        components.removeAll();
    }

    public void onAdd(final ClickEvent event) {
        final ComponentAddPresenter presenter = addPresenterProvider.get();
        final AddSelectionHandler selectionHandler = new AddSelectionHandler(presenter);
        final HandlerRegistration handlerRegistration = presenter.addSelectionChangeHandler(selectionHandler);
        selectionHandler.setHandlerRegistration(handlerRegistration);
        presenter.setTypes(components.getComponentTypes());
        presenter.clearSelection();

        final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();

        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3,
                target.getAbsoluteTop() + target.getClientHeight() + 1);
        ShowPopupEvent.fire(this, presenter, PopupType.POPUP, popupPosition, null, target);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(layoutPresenter);
    }

    @Override
    protected void onRead(final Dashboard dashboard) {
        if (!loaded) {
            loaded = true;

            components.setDashboard(dashboard);
            components.clear();
            LayoutConfig layoutData = null;

            final DashboardConfig dashboardData = dashboard.getDashboardData();
            if (dashboardData != null) {
                layoutData = dashboardData.getLayout();
                final List<ComponentConfig> componentDataList = dashboardData.getComponents();
                if (componentDataList != null) {
                    for (final ComponentConfig componentData : componentDataList) {
                        addComponent(componentData.getType(), componentData);
                    }
                    for (final ComponentConfig componentData : componentDataList) {
                        final Component component = components.get(componentData.getId());
                        if (component != null) {
                            component.link();
                        }
                    }
                }
            } else {
                // /**
                // * ADD TEST DATA
                // */
                // final SplitLayoutData down = new
                // SplitLayoutData(Direction.DOWN.getDimension());
                // for (int i = 0; i < 3; i++) {
                // final SplitLayoutData across = new
                // SplitLayoutData(Direction.ACROSS.getDimension());
                // down.add(across);
                //
                // for (int l = 0; l < 2; l++) {
                // final SplitLayoutData down2 = new
                // SplitLayoutData(Direction.DOWN.getDimension());
                // across.add(down2);
                //
                // for (int j = 0; j < 3; j++) {
                // final TabLayoutData tablayout = new TabLayoutData();
                // down2.add(tablayout);
                //
                // for (int k = 0; k < 2; k++) {
                // final String type = TablePresenter.TYPE;
                // final String id = type + "_" +
                // String.valueOf(System.currentTimeMillis());
                //
                // final ComponentData componentData = new ComponentData();
                // componentData.setId(id);
                //
                // final ComponentPresenter component =
                // componentRegistry.getComponent(type);
                // component.read(componentData);
                //
                // components.add(component);
                // componentViews.put(id, component.getView());
                //
                // final TabData tabData = new TabData();
                // tabData.setId(id);
                // tabData.setName(component.getType() + " " + i + ":" + j + ":"
                // + k);
                //
                // tablayout.add(tabData);
                // }
                // }
                // }
                // }
                // dashboardData.setLayout(down);
                // /**
                // * DONE - ADD TEST DATA
                // */
            }

            // if (dashboardData.getTabVisibility() != null) {
            // tabVisibility.setSelectedItem(dashboardData.getTabVisibility());
            // }

            layoutPresenter.setLayoutData(layoutData);
        }
    }

    private Component addComponent(final String type, final ComponentConfig componentData) {
        final Component component = components.add(type, componentData.getId());
        if (component != null) {
            if (component instanceof HasDirtyHandlers) {
                ((HasDirtyHandlers) component).addDirtyHandler(new DirtyHandler() {
                    @Override
                    public void onDirty(final DirtyEvent event) {
                        setDirty(true);
                    }
                });
            }
            component.read(componentData);
        }

        return component;
    }

    @Override
    protected void onWrite(final Dashboard dashboard) {
        final List<ComponentConfig> componentDataList = new ArrayList<ComponentConfig>(components.size());
        for (final Component component : components) {
            final ComponentConfig componentData = new ComponentConfig();
            component.write(componentData);
            componentDataList.add(componentData);
        }

        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setComponents(componentDataList);
        dashboardData.setLayout(layoutPresenter.getLayoutData());
        dashboardData.setTabVisibility(TabVisibility.SHOW_ALL);
        dashboard.setDashboardData(dashboardData);
    }

    @Override
    protected void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);
        addButton.setEnabled(!readOnly);
        if (!readOnly) {
            registerHandler(addButton.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                        onAdd(event);
                    }
                }
            }));
        }
    }

    @Override
    public void onClose() {
        // Remove all components.
        components.removeAll();
        super.onClose();
    }

    @Override
    public String getType() {
        return Dashboard.ENTITY_TYPE;
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public void requestTabClose(final TabConfig tabData) {
        ConfirmEvent.fire(this, "Are you sure you want to close this tab?", new ConfirmCallback() {
            @Override
            public void onResult(final boolean ok) {
                if (ok) {
                    layoutPresenter.closeTab(tabData);
                    components.remove(tabData.getId(), true);
                }
            }
        });
    }

    private class AddSelectionHandler implements SelectionChangeEvent.Handler {
        private final ComponentAddPresenter presenter;
        private HandlerRegistration handlerRegistration;

        public AddSelectionHandler(final ComponentAddPresenter presenter) {
            this.presenter = presenter;
        }

        @Override
        public void onSelectionChange(final SelectionChangeEvent event) {
            final ComponentType type = presenter.getSelectedObject();
            if (type != null) {
                HidePopupEvent.fire(DashboardPresenter.this, presenter);
                if (handlerRegistration != null) {
                    handlerRegistration.removeHandler();
                }

                String id = type.getId() + "-" + RandomId.getId(5);
                // Make sure we don't duplicate ids.
                while (components.idExists(id)) {
                    id = type.getId() + "-" + RandomId.getId(5);
                }

                final ComponentConfig componentData = new ComponentConfig();
                componentData.setType(type.getId());
                componentData.setId(id);
                componentData.setName(type.getName());

                final Component componentPresenter = addComponent(componentData.getType(), componentData);
                if (componentPresenter != null) {
                    componentPresenter.link();
                }

                final TabConfig tabData = new TabConfig();
                tabData.setId(id);

                final TabLayoutConfig tabLayoutData = new TabLayoutConfig(tabData);

                // Choose where to put the new component in the layout data.
                LayoutConfig layoutData = layoutPresenter.getLayoutData();
                if (layoutData == null) {
                    // There is no existing layout so add the new item as a
                    // single item layout.

                    layoutData = tabLayoutData;

                } else if (layoutData instanceof TabLayoutConfig) {
                    // If the layout is a single item then replace it with a
                    // split layout.
                    final SplitLayoutConfig splitLayoutData = new SplitLayoutConfig(Direction.DOWN.getDimension(),
                            layoutData, tabLayoutData);
                    layoutData = splitLayoutData;
                } else {
                    // If the layout is already a split then add a new component
                    // to the split.
                    final SplitLayoutConfig parent = (SplitLayoutConfig) layoutData;

                    // Add the new component.
                    parent.add(tabLayoutData);

                    // Fix the heights of the components to fit the new
                    // component in.
                    fixHeights(parent);
                }

                layoutPresenter.setLayoutData(layoutData);
                setDirty(true);
            }
        }

        private void fixHeights(final SplitLayoutConfig parent) {
            // Create a default size to use.
            final Size defaultSize = new Size();

            if (parent.count() > 1) {
                final LayoutConfig previousComponent = parent.get(parent.count() - 2);
                final int height = previousComponent.getPreferredSize().getHeight();

                // See if the previous component has enough height to be split
                // to include the new component.
                if (height > (defaultSize.getHeight() * 2)) {
                    previousComponent.getPreferredSize().setHeight(height - defaultSize.getHeight());
                } else {
                    // The previous component isn't high enough so resize all
                    // components to fit.
                    lazyRedistribution(parent);
                }
            }
        }

        private void lazyRedistribution(final SplitLayoutConfig parent) {
            // Create a default size to use.
            final Size defaultSize = new Size();

            // See if we can get the currently presented position and size for
            // the parent layout.
            final PositionAndSize positionAndSize = layoutPresenter.getPositionAndSize(parent);
            if (positionAndSize != null) {
                // Get the current height of the split layout.
                final int height = positionAndSize.getHeight();

                final int totalHeight = getTotalHeight(parent);
                if (height > 0 && totalHeight > height) {
                    int amountToSave = totalHeight - height;

                    // Try and set heights to the default height to claw back
                    // space we want to save.
                    for (int i = parent.count() - 1; i >= 0; i--) {
                        final LayoutConfig ld = parent.get(i);
                        final Size size = ld.getPreferredSize();
                        final int diff = size.getHeight() - defaultSize.getHeight();
                        if (diff > 0) {
                            if (diff > amountToSave) {
                                size.setHeight(size.getHeight() - amountToSave);
                                amountToSave = 0;
                                break;
                            } else {
                                size.setHeight(defaultSize.getHeight());
                                amountToSave -= diff;
                            }
                        }
                    }

                    // If we have more space we need to save then try and
                    // distribute space evenly between widgets.
                    if (amountToSave > 0) {
                        fairRedistribution(parent, height);
                    }
                }
            } else {
                // We have no idea what size the parnet container is occupying
                // so just reset all heights.
                resetAllHeights(parent);
            }
        }

        private void fairRedistribution(final SplitLayoutConfig parent, final int height) {
            // Find out how high each component could be if they were all the
            // same height.
            int fairHeight = (height / parent.count());
            fairHeight = Math.max(0, fairHeight);

            int used = 0;
            int count = 0;

            // Try and find the components that are bigger than their fair size
            // and remember the amount of space used by smaller components.
            for (int i = parent.count() - 1; i >= 0; i--) {
                final LayoutConfig ld = parent.get(i);
                final Size size = ld.getPreferredSize();
                if (size.getHeight() > fairHeight) {
                    count++;
                } else {
                    used += size.getHeight();
                }
            }

            // Calculate the height to set all components that are bigger than
            // the available height.
            if (count > 0) {
                final int newHeight = ((height - used) / count);
                for (int i = parent.count() - 1; i >= 0; i--) {
                    final LayoutConfig ld = parent.get(i);
                    final Size size = ld.getPreferredSize();
                    if (size.getHeight() > fairHeight) {
                        size.setHeight(newHeight);
                    }
                }
            }
        }

        private void resetAllHeights(final SplitLayoutConfig parent) {
            final Size defaultSize = new Size();
            for (int i = 0; i < parent.count(); i++) {
                final LayoutConfig ld = parent.get(i);
                final Size size = ld.getPreferredSize();
                if (size.getHeight() > defaultSize.getHeight()) {
                    size.setHeight(defaultSize.getHeight());
                }
            }
        }

        private int getTotalHeight(final SplitLayoutConfig parent) {
            int totalHeight = 0;
            for (int i = parent.count() - 1; i >= 0; i--) {
                final LayoutConfig ld = parent.get(i);
                final Size size = ld.getPreferredSize();
                totalHeight += size.getHeight();
            }
            return totalHeight;
        }

        public void setHandlerRegistration(final HandlerRegistration handlerRegistration) {
            this.handlerRegistration = handlerRegistration;
        }
    }
}
