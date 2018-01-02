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

package stroom.widget.iframe.client.view;

import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class IFrameContentViewImpl extends ViewImpl implements IFrameContentView {
    private final ResizeSimplePanel widget;
    private Frame frame;

    @Inject
    public IFrameContentViewImpl() {
        frame = new Frame();
        final com.google.gwt.dom.client.Style style = frame.getElement().getStyle();
        style.setPosition(Position.ABSOLUTE);
        style.setOpacity(1);
        style.setZIndex(2);
        style.setWidth(100, Unit.PCT);
        style.setHeight(100, Unit.PCT);
        style.setBorderWidth(0, Unit.PX);
        frame.setVisible(false);
        RootPanel.get().add(frame);

        widget = new ResizeSimplePanel() {
            @Override
            public void onResize() {
                resize();
            }

            @Override
            protected void onAttach() {
                super.onAttach();
                frame.setVisible(true);
                resize();
            }

            @Override
            protected void onDetach() {
                super.onDetach();
                frame.setVisible(false);
            }
        };

        widget.getElement().getStyle().setWidth(100, Unit.PCT);
        widget.getElement().getStyle().setHeight(100, Unit.PCT);
        widget.getElement().getStyle().setBorderWidth(0, Unit.PX);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setUrl(final String url) {
        frame.setUrl(url);
    }

    @Override
    public void cleanup() {
        RootPanel.get().remove(frame);
    }

    private void resize() {
        if (widget.getOffsetWidth() > 0 && widget.getOffsetHeight() > 0) {
            final com.google.gwt.dom.client.Style style = frame.getElement().getStyle();
            style.setLeft(widget.getElement().getAbsoluteLeft(), Unit.PX);
            style.setTop(widget.getElement().getAbsoluteTop(), Unit.PX);
            style.setWidth(widget.getElement().getClientWidth(), Unit.PX);
            style.setHeight(widget.getElement().getClientHeight(), Unit.PX);
        }
    }
}
