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

package stroom.search.elastic.client.view;

import stroom.item.client.MultiSelectionBox;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListPresenter.ElasticIndexFieldListView;
import stroom.search.elastic.client.presenter.ElasticIndexFieldListUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ResizeFlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.Objects;

/// The view of the Elasticsearch index Fields tab.
public class ElasticIndexFieldListViewImpl
        extends ViewWithUiHandlers<ElasticIndexFieldListUiHandlers>
        implements ElasticIndexFieldListView {

    private static final String ALL_TYPES = "All types";

    private final Widget widget;

    @UiField
    QuickFilter nameFilter;
    @UiField
    MultiSelectionBox<String> typeFilter;
    @UiField
    SimplePanel dataGrid;
    @UiField
    LinkTabBar tabBar;
    @UiField
    SimplePanel infoPanel;
    @UiField
    SimplePanel settingsPanel;
    @UiField
    ResizeFlowPanel detailsTabs;
    @UiField
    Label noSelection;

    private List<String> typeFilterOptions;

    @Inject
    public ElasticIndexFieldListViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        // Ticking no types shows all of them
        typeFilter.setNoneSelectedText(ALL_TYPES);
        tabBar.addSelectionHandler(this::onTabSelection);
        // The tab bar only lays out its tabs once it has a width, so lay it out again once it is shown
        widget.addAttachHandler(event -> {
            if (event.isAttached()) {
                resizeTabBar();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDataGridView(final View view) {
        dataGrid.setWidget(view.asWidget());
    }

    @Override
    public void setTypeFilterOptions(final List<String> options) {
        // Only replace the items when they change, so the popup isn't redrawn while the user ticks items
        if (!Objects.equals(typeFilterOptions, options)) {
            typeFilterOptions = options;
            typeFilter.setItems(options);
        }
    }

    @Override
    public void clearFilters() {
        nameFilter.setText("", false);
        typeFilter.setValue(List.of());
    }

    @Override
    public void addDetailsTab(final TabData tab) {
        tabBar.addTab(tab);
    }

    @Override
    public void selectDetailsTab(final TabData tab) {
        tabBar.selectTab(tab);
        final boolean info = tab == ElasticIndexFieldListPresenter.INFO_TAB;
        infoPanel.setVisible(info);
        settingsPanel.setVisible(!info);
    }

    @Override
    public void setInfoView(final View view) {
        infoPanel.setWidget(view.asWidget());
    }

    @Override
    public void setSettingsView(final View view) {
        settingsPanel.setWidget(view.asWidget());
    }

    @Override
    public void setDetailsVisible(final boolean visible) {
        final boolean wasVisible = detailsTabs.isVisible();
        detailsTabs.setVisible(visible);
        noSelection.setVisible(!visible);
        if (visible && !wasVisible) {
            resizeTabBar();
        }
    }

    private void resizeTabBar() {
        Scheduler.get().scheduleDeferred(tabBar::onResize);
    }

    private void onTabSelection(final SelectionEvent<TabData> event) {
        if (getUiHandlers() != null && event.getSelectedItem() != null) {
            getUiHandlers().onDetailsTabSelected(event.getSelectedItem());
        }
    }

    @UiHandler("nameFilter")
    void onNameFilterChange(final ValueChangeEvent<String> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onNameFilterChange(nameFilter.getText());
        }
    }

    @UiHandler("typeFilter")
    void onTypeFilterChange(final ValueChangeEvent<List<String>> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTypeFilterChange(typeFilter.getValue());
        }
    }

    /// UiBinder for [ElasticIndexFieldListViewImpl].
    public interface Binder extends UiBinder<Widget, ElasticIndexFieldListViewImpl> {

    }
}
