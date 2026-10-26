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
import stroom.data.grid.client.MessagePanelImpl;
import stroom.data.pager.client.RefreshButton;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class VisViewImpl extends ViewImpl implements VisView {

    private final FlowPanel widget;
    private final SimplePanel visContainer;
    private final MessagePanelImpl messagePanel;
    private final RefreshButton refreshButton;
    // The chart's controls, under it.
    private final VisToolbar toolbar;

    private VisFrame visFrame;

    @Inject
    public VisViewImpl() {
        messagePanel = new MessagePanelImpl();

        visContainer = new SimplePanel();
        visContainer.setStyleName("dashboardVis-innerLayout");

        refreshButton = new RefreshButton();
        refreshButton.setAllowPause(true);

        // The refresh button is on the toolbar's right, as a table's is.
        toolbar = new VisToolbar(refreshButton);

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
                // The frame floats over the pane, placed where the pane is. A pane put back in a layout that was
                // built again (e.g. once a component is added as a tab beside it) may be the same size somewhere
                // else, which the resize observer doesn't report: place the frame again once it is laid out.
                Scheduler.get().scheduleDeferred(() -> {
                    if (isAttached() && widget.getOffsetWidth() > 0 && widget.getOffsetHeight() > 0) {
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
        widget.add(messagePanel);
        widget.add(toolbar);
        // The chart's frame is placed over its container, which leaves room for the toolbar under it.
        widget.addStyleName("dashboardVis-outerLayout--toolbar");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public RefreshButton getRefreshButton() {
        return refreshButton;
    }

    public void setVisFrame(final VisFrame visFrame) {
        this.visFrame = visFrame;
        toolbar.setVisFrame(visFrame);
        onResize();
    }

    @Override
    public void onResize() {
        if (visFrame != null) {
            final Element ref = visContainer.getElement();
            //getParentByClass(visContainer.getElement(), "tabLayout-contentOuter");
            final Element dashboard = getParentByClass(ref, "dashboard-scrollPanel");

            if (ref != null) {
                final Rect inner = withinTabContent(ref, ElementUtil.getBoundingClientRectPlusWindowScroll(ref));
                if (dashboard != null) {
                    final Rect outer = ElementUtil.getClientRect(dashboard);
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

    // The pane's layout is positioned within its whole tab layout, tab bar included, so the frame placed over it
    // would cover the tab bar: keep it to the tab's content area, below the bar.
    private Rect withinTabContent(final Element ref, final Rect rect) {
        Element content = ref;
        while (content != null && !content.hasClassName("tabLayout-content")) {
            content = content.getParentElement();
        }
        return content == null
                ? rect
                : Rect.min(rect, ElementUtil.getBoundingClientRectPlusWindowScroll(content));
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
        messagePanel.showMessage(msg);
    }

    @Override
    public void hideMessage() {
        messagePanel.hideMessage();
    }
}
