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

package stroom.dashboard.client.vis;

import stroom.dashboard.client.vis.VisPresenter.VisView;
import stroom.widget.layout.client.view.ResizeFlowPanel;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class VisViewImpl extends ViewImpl implements VisView {

    private final ResizeFlowPanel widget;
    private final SimplePanel visContainer;
    private final SpinnerSmall spinnerSmall;
    private final SimplePanel messagePanel;
    private final Label message;

    private VisPane visPane;

    @Inject
    public VisViewImpl() {
        message = new Label("", false);
        message.setStyleName("dashboardVis-messageInner");

        messagePanel = new SimplePanel();
        messagePanel.setStyleName("dashboardVis-messageOuter");
        messagePanel.add(message);
        messagePanel.setVisible(false);

        visContainer = new SimplePanel();
        visContainer.setStyleName("dashboardVis-innerLayout");

        spinnerSmall = new SpinnerSmall();
        spinnerSmall.setStyleName("dashboardVis-smallSpinner");
        spinnerSmall.setVisible(false);

        widget = new ResizeFlowPanel() {
            @Override
            public void onResize() {
                if (widget.getOffsetWidth() > 0 && widget.getOffsetHeight() > 0) {
                    resize();
                }
            }

            @Override
            protected void onAttach() {
                super.onAttach();
                if (visPane != null) {
                    visPane.asWidget().setVisible(true);
                }
            }

            @Override
            protected void onDetach() {
                super.onDetach();
                if (visPane != null) {
                    visPane.asWidget().setVisible(false);
                }
            }
        };
        widget.setStyleName("dashboardVis-outerLayout");
        widget.add(visContainer);
        widget.add(spinnerSmall);
        widget.add(messagePanel);

        // Window.alert("d3.js current version " + D3.version());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        spinnerSmall.setVisible(refreshing);
    }

    @Override
    public void setVisPane(final VisPane visPane) {
        this.visPane = visPane;
        resize();
    }

    private void resize() {
        if (visPane != null) {
            final Style style = visPane.asWidget().getElement().getStyle();
            Element ref = visContainer.getElement();
            while (ref != null && (ref.getClassName() == null || !ref.getClassName().contains("tabLayout-contentInner"))) {
                ref = ref.getParentElement();
            }

            if (ref != null) {
                style.setLeft(ref.getAbsoluteLeft(), Unit.PX);
                style.setTop(ref.getAbsoluteTop(), Unit.PX);
                style.setWidth(ref.getClientWidth(), Unit.PX);
                style.setHeight(ref.getClientHeight(), Unit.PX);

                if (visPane instanceof RequiresResize) {
                    visPane.onResize();
                }
            } else {
                style.setLeft(-1000, Unit.PX);
                style.setTop(-1000, Unit.PX);
                style.setWidth(1000, Unit.PX);
                style.setHeight(1000, Unit.PX);

                if (visPane instanceof RequiresResize) {
                    visPane.onResize();
                }
            }
        }
    }

    @Override
    public void showMessage(final String msg) {
        message.setText(msg);
        messagePanel.setVisible(true);
    }

    @Override
    public void hideMessage() {
        messagePanel.setVisible(false);
    }
}
