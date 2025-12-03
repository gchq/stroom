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

package stroom.iframe.client.view;

import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.presenter.IFrameLoadUiHandlers;
import stroom.widget.tab.client.view.GlobalResizeObserver;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Objects;

public class IFrameContentViewImpl extends ViewWithUiHandlers<IFrameLoadUiHandlers> implements IFrameContentView {

    private static final String DEFAULT_TITLE = "Loading...";

    private final SimplePanel widget;
    private final Frame frame;
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;

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

        widget = new SimplePanel() {
            @Override
            protected void onAttach() {
                super.onAttach();
                frame.setVisible(true);
                resize();
                GlobalResizeObserver.addListener(widget.getElement(), element -> resize());
            }

            @Override
            protected void onDetach() {
                GlobalResizeObserver.removeListener(widget.getElement());
                super.onDetach();
                frame.setVisible(false);
            }
        };

        widget.getElement().getStyle().setWidth(100, Unit.PCT);
        widget.getElement().getStyle().setHeight(100, Unit.PCT);
        widget.getElement().getStyle().setBorderWidth(0, Unit.PX);

        final RepeatingCommand updateTitleCommand = () -> {
            final IFrameElement iFrameElement = frame.getElement().cast();
            try {
                final String title = iFrameElement.getContentDocument().getTitle();
                if (title != null && title.trim().length() > 0) {
                    if (!Objects.equals(lastTitle, title)) {
                        lastTitle = title;
                        getUiHandlers().onTitleChange(title);
                    }
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
            return updateTitle && customTitle == null;
        };
        Scheduler.get().scheduleFixedDelay(updateTitleCommand, 1000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setUrl(final String url) {
        lastTitle = url;
        frame.setUrl(url);
    }

    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
    }

    @Override
    public String getTitle() {
        if (customTitle != null) {
            return customTitle;
        }
        return lastTitle;
    }

    @Override
    public void cleanup() {
        RootPanel.get().remove(frame);
        updateTitle = false;
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
