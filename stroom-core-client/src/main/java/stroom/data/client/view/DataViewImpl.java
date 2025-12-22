/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.client.view;

import stroom.data.client.presenter.DataPresenter.DataView;
import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.tab.client.view.LinkTabBar;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewImpl;

public class DataViewImpl extends ViewImpl implements DataView {

    private final Widget widget;

    @UiField
    LinkTabBar tabBar;
    @UiField
    Label sourceLinkLabel;
    @UiField
    SimplePanel navigatorContainer;
    @UiField
    LayerContainer layerContainer;
    @UiField
    SimplePanel progressBarPanel;
    @UiField
    ButtonPanel buttonPanel;
    @UiField
    SpinnerLarge spinner;

    private boolean sourceLinkEnabled = true;


    @Inject
    public DataViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        spinner.setVisible(false);
        layerContainer.setFade(true);
        sourceLinkLabel.setText("View Source");
        sourceLinkLabel.setVisible(true);
    }

    @Override
    public void focus() {
        tabBar.focus();
    }

    @Override
    public void setSourceLinkVisible(final boolean isVisible, final boolean isEnabled) {
        sourceLinkLabel.setVisible(isVisible);

        if (isEnabled) {
            sourceLinkEnabled = true;
            sourceLinkLabel.addStyleName("enabled");
            sourceLinkLabel.removeStyleName("disabled");
        } else {
            sourceLinkEnabled = false;
            sourceLinkLabel.removeStyleName("enabled");
            sourceLinkLabel.addStyleName("disabled");
        }
    }

    @Override
    public void addSourceLinkClickHandler(final ClickHandler clickHandler) {
        if (clickHandler != null) {
            sourceLinkLabel.addClickHandler(event -> {
                if (sourceLinkEnabled) {
                    clickHandler.onClick(event);
                }
            });
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public LinkTabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    @Override
    public void setNavigatorView(final ItemNavigatorView itemNavigatorView) {
        if (itemNavigatorView != null) {
            navigatorContainer.setWidget(itemNavigatorView.asWidget());
        } else {
            navigatorContainer.clear();
        }
    }

    @Override
    public void setProgressView(final ProgressView progressView) {
        if (progressView != null) {
            progressBarPanel.setWidget(progressView.asWidget());
        } else {
            progressBarPanel.clear();
        }
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    public interface Binder extends UiBinder<Widget, DataViewImpl> {

    }
}
