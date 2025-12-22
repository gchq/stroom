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

package stroom.query.client.view;

import stroom.dashboard.client.vis.VisFrame;
import stroom.data.grid.client.MessagePanelImpl;
import stroom.data.pager.client.RefreshButton;
import stroom.query.client.presenter.QueryResultVisPresenter.QueryResultVisView;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class QueryResultVisViewImpl extends ViewImpl implements QueryResultVisView {

    private final FlowPanel widget;
    private final SimplePanel visContainer;
    private final MessagePanelImpl messagePanel;
    private final RefreshButton refreshButton;

    private VisFrame visFrame;

    @Inject
    public QueryResultVisViewImpl() {
        messagePanel = new MessagePanelImpl();

        visContainer = new SimplePanel();
        visContainer.setStyleName("queryVis-innerLayout");

        refreshButton = new RefreshButton();
        refreshButton.addStyleName("dashboardVis-refresh");
        refreshButton.setAllowPause(true);

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
        widget.setStyleName("queryVis-outerLayout"); //("dashboardVis-outerLayout");
        widget.add(visContainer);
        widget.add(refreshButton);
        widget.add(messagePanel);
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
        onResize();
    }

    @Override
    public void onResize() {
        if (visFrame != null) {
            final Element ref = visContainer.getElement();
            //getParentByClass(visContainer.getElement(), "tabLayout-contentOuter");
            final Element dashboard = getParentByClass(ref, "dashboard-scrollPanel");

            if (ref != null) {
                final Rect inner = ElementUtil.getBoundingClientRectPlusWindowScroll(ref);
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
