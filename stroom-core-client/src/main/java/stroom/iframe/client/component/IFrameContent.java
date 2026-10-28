/*
 * Copyright 2018 Crown Copyright
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

package stroom.iframe.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A page shown as one of Stroom's tabs. The page's frame is over the tab, outside it, so that the
/// page isn't reloaded each time the tab is shown: it is shown while the tab is, at the tab's place
/// and size. The tab is named for the page's title until the page has one.
public class IFrameContent extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String DEFAULT_TITLE = "Loading...";

    private final Frame frame;
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;
    private Runnable onTitleChange = () -> {
    };

    /// Makes the tab, with a page's frame over the app, hidden until the tab is shown.
    public IFrameContent() {
        initWidget(BINDER.createAndBindUi(this));
        frame = new Frame();
        frame.setStyleName("iframe-content__frame");
        frame.setVisible(false);
        RootPanel.get().add(frame);

        // The page's title is polled: a page from elsewhere can't tell us when it changes
        final RepeatingCommand updateTitleCommand = () -> {
            final IFrameElement iFrameElement = frame.getElement().cast();
            try {
                final String title = iFrameElement.getContentDocument().getTitle();
                if (title != null && !title.trim().isEmpty()) {
                    if (!Objects.equals(lastTitle, title)) {
                        lastTitle = title;
                        updateFrameName();
                        onTitleChange.run();
                    }
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
            return updateTitle && customTitle == null;
        };
        Scheduler.get().scheduleFixedDelay(updateTitleCommand, 1000);
    }

    /// @param onTitleChange Run when the page's title changes, to rename the tab.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = Objects.requireNonNull(onTitleChange, "onTitleChange");
    }

    /// @param url The page to show.
    public void setUrl(final String url) {
        lastTitle = url;
        updateFrameName();
        frame.setUrl(url);
    }

    /// @param customTitle The tab's name, in place of the page's title, or null for the page's.
    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
        updateFrameName();
    }

    // Names the frame for assistive technology, which announces a frame by its title (WCAG 4.1.2),
    // as the tab is named
    private void updateFrameName() {
        frame.setTitle(getTitle());
    }

    /// @return The tab's name: its custom title, or the page's title, or its URL until the page has a
    /// title.
    public String getTitle() {
        if (customTitle != null) {
            return customTitle;
        }
        return lastTitle;
    }

    /// Removes the page's frame and stops polling its title, once the tab is closed.
    public void cleanup() {
        RootPanel.get().remove(frame);
        updateTitle = false;
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        frame.setVisible(true);
        resize();
        GlobalResizeObserver.addListener(getElement(), element -> resize());
    }

    @Override
    protected void onUnload() {
        GlobalResizeObserver.removeListener(getElement());
        super.onUnload();
        frame.setVisible(false);
    }

    private void resize() {
        final Element element = getElement();
        if (getOffsetWidth() > 0 && getOffsetHeight() > 0) {
            final Element frameElement = frame.getElement();
            CssProperties.set(frameElement, "--iframe-content__left", element.getAbsoluteLeft() + "px");
            CssProperties.set(frameElement, "--iframe-content__top", element.getAbsoluteTop() + "px");
            CssProperties.set(frameElement, "--iframe-content__width", element.getClientWidth() + "px");
            CssProperties.set(frameElement, "--iframe-content__height", element.getClientHeight() + "px");
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IFrameContent> {

    }
}
