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

package stroom.main.client.view;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.main.client.presenter.MainPresenter;
import stroom.main.client.presenter.MainPresenter.SpinnerDisplay;
import stroom.util.shared.EqualsUtil;

public class MainViewImpl extends ViewImpl implements MainPresenter.MainView {
    public interface Binder extends UiBinder<Widget, MainViewImpl> {
    }

    private final Widget widget;

    @UiField
    SimplePanel banner;
    @UiField
    FlowPanel main;
    @UiField
    Spinner spinner;
    @UiField
    SimplePanel topPanel;
    @UiField
    ResizeLayoutPanel contentPanel;

    private View maximisedView;
    private int splitPos = 300;

    private MySplitLayoutPanel splitPanel;
    private Widget westWidget;
    private Widget centerWidget;
    private String currentBanner;

    @Inject
    public MainViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);
        banner.setVisible(false);
        widget.sinkEvents(Event.KEYEVENTS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (slot == MainPresenter.MENUBAR) {
            topPanel.add(content);
        } else if (slot == MainPresenter.EXPLORER) {
            westWidget = content;
            showSplit();
        } else if (slot == MainPresenter.CONTENT) {
            centerWidget = content;
            showSplit();
        } else {
            super.setInSlot(slot, content);
        }
    }

    @Override
    public void maximise(final View view) {
        if (maximisedView == null || maximisedView != view) {
            // Remember split panel.
            if (westWidget != null) {
                splitPos = westWidget.getOffsetWidth();
            }

            // Maximise the passed view.
            contentPanel.setWidget(view.asWidget());

            // Clear the split panel.
            hideSplit();

            maximisedView = view;
        } else if (maximisedView == view) {
            // Restore the view.
            showSplit();

            maximisedView = null;
        }
    }

    private void showSplit() {
        // Ensure we the split position isn't too small.
        if (splitPos < 10) {
            splitPos = 10;
        }

        splitPanel = new MySplitLayoutPanel();
        if (westWidget != null) {
            splitPanel.addWest(westWidget, splitPos);
        }
        if (centerWidget != null) {
            splitPanel.add(centerWidget);
        }

        contentPanel.clear();
        contentPanel.setWidget(splitPanel);
    }

    private void hideSplit() {
        if (splitPanel != null) {
            splitPanel.clear();
            splitPanel = null;
        }
    }

    @Override
    public SpinnerDisplay getSpinner() {
        return spinner;
    }

    @Override
    public void setBanner(final String text) {
        if (!EqualsUtil.isEquals(currentBanner, text)) {
            currentBanner = text;
            if (text == null || text.trim().length() == 0) {
                main.getElement().getStyle().setTop(0, Unit.PX);
                banner.setVisible(false);
                banner.getElement().setInnerText("");
            } else {
                main.getElement().getStyle().setTop(20, Unit.PX);
                banner.setVisible(true);
                banner.getElement().setInnerText(text);
            }
        }
    }
}
