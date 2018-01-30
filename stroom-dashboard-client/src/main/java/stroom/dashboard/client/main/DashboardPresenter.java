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
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.dashboard.client.flexlayout.FlexLayoutChangeHandler;
import stroom.dashboard.client.flexlayout.PositionAndSize;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.query.QueryInfoPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardConfig.TabVisibility;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.SplitLayoutConfig.Direction;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.entity.client.EntityTabData;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.event.SaveEntityEvent;
import stroom.entity.client.event.ShowSaveAsEntityDialogEvent;
import stroom.entity.client.presenter.EntityEditPresenter;
import stroom.explorer.shared.DocumentType;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgIcon;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.util.client.ImageUtil;
import stroom.util.client.RandomId;
import stroom.util.shared.EqualsUtil;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

public class DashboardPresenter extends EntityEditPresenter<DashboardPresenter.DashboardView, Dashboard>
        implements FlexLayoutChangeHandler, EntityTabData, DashboardUiHandlers {
    private final ButtonView saveButton;
    private final ButtonView saveAsButton;
    private final DashboardLayoutPresenter layoutPresenter;
    private final Provider<ComponentAddPresenter> addPresenterProvider;
    private final Components components;
    private final Provider<QueryInfoPresenter> queryInfoPresenterProvider;
    private final ButtonView addButton;
    private ButtonPanel leftButtons;
    private ButtonPanel rightButtons;
    private String lastLabel;
    private boolean loaded;

    private String currentParams;
    private String lastUsedQueryInfo;

    @Inject
    public DashboardPresenter(final EventBus eventBus,
                              final DashboardView view,
                              final DashboardLayoutPresenter layoutPresenter,
                              final Provider<ComponentAddPresenter> addPresenterProvider,
                              final Components components,
                              final Provider<QueryInfoPresenter> queryInfoPresenterProvider,
                              final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.layoutPresenter = layoutPresenter;
        this.addPresenterProvider = addPresenterProvider;
        this.components = components;
        this.queryInfoPresenterProvider = queryInfoPresenterProvider;

        saveButton = addButtonLeft(SvgPresets.SAVE);
        saveAsButton = addButtonLeft(SvgPresets.SAVE_AS);
        saveButton.setEnabled(false);
        saveAsButton.setEnabled(false);

        registerHandler(saveButton.addClickHandler(event -> {
            if (saveButton.isEnabled()) {
                SaveEntityEvent.fire(DashboardPresenter.this, DashboardPresenter.this);
            }
        }));
        registerHandler(saveAsButton.addClickHandler(event -> {
            if (saveAsButton.isEnabled()) {
                ShowSaveAsEntityDialogEvent.fire(DashboardPresenter.this, DashboardPresenter.this);
            }
        }));

        layoutPresenter.setFlexLayoutChangeHandler(this);
        layoutPresenter.setComponents(components);
        view.setContent(layoutPresenter.getView());

        addButton = addButtonLeft(SvgPresets.ADD);
        addButton.setTitle("Add Component");
        addButton.setEnabled(false);

        view.setUiHandlers(this);
    }

    private ButtonView addButtonLeft(final SvgPreset preset) {
        if (leftButtons == null) {
            leftButtons = new ButtonPanel();
            leftButtons.getElement().getStyle().setPaddingLeft(1, Style.Unit.PX);
            addWidgetLeft(leftButtons);
        }

        return leftButtons.add(preset);
    }

    private void addWidgetLeft(final Widget widget) {
        getView().addWidgetLeft(widget);
    }

    private void addWidgetRight(final Widget widget) {
        getView().addWidgetRight(widget);
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();

        // Remove all components. This should have been done already in the
        // onClose() method.
        components.removeAll();
    }

    private void onAdd(final ClickEvent event) {
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
    protected void onRead(final Dashboard dashboard) {
        if (!loaded) {
            loaded = true;

            components.setDashboard(dashboard);
            components.clear();
            LayoutConfig layoutData = null;

            final DashboardConfig dashboardData = dashboard.getDashboardData();
            if (dashboardData != null) {
                currentParams = "";
                if (dashboardData.getParameters() != null && dashboardData.getParameters().trim().length() > 0) {
                    currentParams = dashboardData.getParameters().trim();
                }
                getView().setParams(currentParams);

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
                ((HasDirtyHandlers) component).addDirtyHandler(event -> setDirty(true));
            }

            // Set params on the component if it needs them.
            if (component instanceof Queryable) {
                ((Queryable) component).onQuery(currentParams, null);
            }

            component.read(componentData);
        }

        return component;
    }

    @Override
    protected void onWrite(final Dashboard dashboard) {
        String params = getView().getParams();
        if (params != null && params.trim().length() == 0) {
            params = null;
        }

        final List<ComponentConfig> componentDataList = new ArrayList<>(components.size());
        for (final Component component : components) {
            final ComponentConfig componentData = new ComponentConfig();
            component.write(componentData);
            componentDataList.add(componentData);
        }

        final DashboardConfig dashboardData = new DashboardConfig();
        dashboardData.setParameters(params);
        dashboardData.setComponents(componentDataList);
        dashboardData.setLayout(layoutPresenter.getLayoutData());
        dashboardData.setTabVisibility(TabVisibility.SHOW_ALL);
        dashboard.setDashboardData(dashboardData);
    }

    @Override
    public void onPermissionsCheck(final boolean readOnly) {
        super.onPermissionsCheck(readOnly);

        saveButton.setEnabled(isDirty() && !readOnly);
        saveAsButton.setEnabled(true);

        addButton.setEnabled(!readOnly);
        if (!readOnly) {
            registerHandler(addButton.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onAdd(event);
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
        ConfirmEvent.fire(this, "Are you sure you want to close this tab?", ok -> {
            if (ok) {
                layoutPresenter.closeTab(tabData);
                components.remove(tabData.getId(), true);
            }
        });
    }

    @Override
    public void onParamsChanged(final String params) {
        String trimmed = "";
        if (params != null && params.trim().length() > 0) {
            trimmed = params.trim();
        }

        if (!EqualsUtil.isEquals(currentParams, trimmed)) {
            setDirty(true);

            currentParams = trimmed;

            // Get a sub list of components that can be queried.
            final List<Queryable> queryableComponents = new ArrayList<>();
            for (final Component component : components) {
                if (component instanceof Queryable) {
                    queryableComponents.add((Queryable) component);
                }
            }

            // If we have some queryable components then make sure we get query info for them.
            if (queryableComponents.size() > 0) {
                queryInfoPresenterProvider.get().show(lastUsedQueryInfo, state -> {
                    if (state.isOk()) {
                        lastUsedQueryInfo = state.getQueryInfo();
                        for (final Queryable queryable : queryableComponents) {
                            queryable.onQuery(currentParams, lastUsedQueryInfo);
                        }
                    }
                });
            }
        }
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + getEntity().getName();
        }

        return getEntity().getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public Icon getIcon() {
        return new SvgIcon(ImageUtil.getImageURL() + DocumentType.DOC_IMAGE_URL + getType() + ".svg", 18, 18);
    }

    @Override
    public void onDirty(final boolean dirty) {
        if (!isReadOnly()) {
            // Only fire tab refresh if the tab has changed.
            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }

            saveButton.setEnabled(dirty);
        }
    }

    public interface DashboardView extends View, HasUiHandlers<DashboardUiHandlers> {
        void addWidgetLeft(Widget widget);

        void addWidgetRight(Widget widget);

        String getParams();

        void setParams(String params);

        void setContent(View view);
    }

    private class AddSelectionHandler implements SelectionChangeEvent.Handler {
        private final ComponentAddPresenter presenter;
        private HandlerRegistration handlerRegistration;

        AddSelectionHandler(final ComponentAddPresenter presenter) {
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

                String id = type.getId() + "-" + RandomId.createId(5);
                // Make sure we don't duplicate ids.
                while (components.idExists(id)) {
                    id = type.getId() + "-" + RandomId.createId(5);
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
                    layoutData = new SplitLayoutConfig(Direction.DOWN.getDimension(),
                            layoutData, tabLayoutData);
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

        void setHandlerRegistration(final HandlerRegistration handlerRegistration) {
            this.handlerRegistration = handlerRegistration;
        }
    }
}
