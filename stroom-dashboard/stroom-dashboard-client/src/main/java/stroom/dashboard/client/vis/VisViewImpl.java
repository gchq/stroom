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
import stroom.svg.client.SvgImages;
import stroom.widget.spinner.client.SpinnerSmall;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class VisViewImpl extends ViewWithUiHandlers<VisUiHandlers>
        implements VisView {

    private final FlowPanel widget;
    private final SimplePanel visContainer;
    private final SimplePanel messagePanel;
    private final Label message;

    private VisFrame visFrame;

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

        final SpinnerSmall spinnerSmall = new SpinnerSmall();
        spinnerSmall.setStyleName("dashboardVis-smallSpinner");
        spinnerSmall.setTitle("Pause Update");

        final Button pause = new Button();
        pause.setStyleName("dashboardVis-pause svg-image-button");
        pause.getElement().setInnerHTML(SvgImages.MONO_PAUSE);
        pause.setTitle("Resume Update");

        widget = new FlowPanel() {
            @Override
            protected void onAttach() {
                super.onAttach();
                if (visFrame != null) {
                    visFrame.asWidget().setVisible(true);
                }
                GlobalResizeObserver.addListener(getElement(), element -> {
                    if (widget.getOffsetWidth() > 0 && widget.getOffsetHeight() > 0) {
                        onResize();
                    }
                });
            }

            @Override
            protected void onDetach() {
                GlobalResizeObserver.removeListener(getElement());
                super.onDetach();
                if (visFrame != null) {
                    visFrame.asWidget().setVisible(false);
                }
            }
        };
        widget.setStyleName("dashboardVis-outerLayout");
        widget.add(visContainer);
        widget.add(spinnerSmall);
        widget.add(pause);
        widget.add(messagePanel);

        spinnerSmall.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onPause();
            }
        }, ClickEvent.getType());
        pause.addDomHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onPause();
            }
        }, ClickEvent.getType());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            widget.addStyleName("refreshing");
        } else {
            widget.removeStyleName("refreshing");
        }
    }

    @Override
    public void setPaused(final boolean paused) {
        if (paused) {
            widget.addStyleName("paused");
        } else {
            widget.removeStyleName("paused");
        }
    }

    public void setVisFrame(final VisFrame visFrame) {
        this.visFrame = visFrame;
        onResize();
    }

    @Override
    public void onResize() {
        if (visFrame != null) {
            final Element ref = getParentByClass(visContainer.getElement(), "tabLayout-contentOuter");
            final Element dashboard = getParentByClass(ref, "dashboard-scrollPanel");

            if (ref != null) {
                final Rect inner = ElementUtil.getInnerClientRect(ref);
                if (dashboard != null) {
                    final Rect outer = ElementUtil.getInnerClientRect(dashboard);
                    final Rect min = Rect.min(outer, inner);

                    visFrame.setContainerPositionAndSize(
                            min.getLeft(),
                            min.getTop(),
                            min.getWidth(),
                            min.getHeight());

                    visFrame.setInnerPositionAndSize(
                            Math.min(0, inner.getLeft() - outer.getLeft()),
                            Math.min(0, inner.getTop() - outer.getTop()),
                            inner.getWidth(),
                            inner.getHeight());
                } else {
                    visFrame.setInnerPositionAndSize(
                            inner.getLeft(),
                            inner.getTop(),
                            inner.getWidth(),
                            inner.getHeight());
                }

                visFrame.onResize();

            } else {
                visFrame.setContainerPositionAndSize(
                        -1000,
                        -1000,
                        1000,
                        1000);

                visFrame.onResize();
            }
        }
    }

    private Element getParentByClass(final Element element, final String className) {
        Element el = element;
        while (el != null &&
                (el.getClassName() == null ||
                        !el.getClassName().contains(className))) {
            el = el.getParentElement();
        }
        return el;
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
