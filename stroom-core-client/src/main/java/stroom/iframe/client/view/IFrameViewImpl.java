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

package stroom.iframe.client.view;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.iframe.client.presenter.IFrameLoadUiHandlers;
import stroom.iframe.client.presenter.IFramePresenter.IFrameView;

import java.util.Objects;

public class IFrameViewImpl extends ViewWithUiHandlers<IFrameLoadUiHandlers> implements IFrameView {
    private static final String DEFAULT_TITLE = "Loading...";

    private final SimplePanel panel = new SimplePanel();
    private final Frame frame = new Frame();
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;

    @Inject
    public IFrameViewImpl() {
        setFrameStyle(frame.getElement().getStyle());
        setPanelStyle(panel.getElement().getStyle());

        panel.setWidget(frame);

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

    private void setFrameStyle(final Style style) {
        style.setWidth(100, Unit.PCT);
        style.setHeight(100, Unit.PCT);
        style.setBorderStyle(BorderStyle.NONE);
    }

    private void setPanelStyle(final Style style) {
        style.setWidth(100, Unit.PCT);
        style.setHeight(100, Unit.PCT);
        style.setBorderWidth(1, Unit.PX);
        style.setBorderStyle(BorderStyle.SOLID);
        style.setBorderColor("#C5CDE2");
    }

    @Override
    public Widget asWidget() {
        return panel;
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
        updateTitle = false;
    }
}
