/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.layout.client;

import stroom.widget.util.client.GlobalResizeObserver;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

/// A scroll panel that fills its parent, its content at least as wide and tall as it
/// (`scroll-pane.css`), e.g. a tree or a list that scrolls in a dialog:
///
/// ```
/// <l:ScrollPane addStyleNames="dock-max form-control-border">
///     <g:SimplePanel ui:field="tree"/>
/// </l:ScrollPane>
/// ```
///
/// Give it more classes with `addStyleNames` (not `styleName`, which would replace its own). A pane
/// sized by its own classes or its content instead, e.g. a box of error text, is made with
/// `fitContent="true"`.
///
/// While its content is bigger than it and holds nothing that takes the focus, the pane is a tab
/// stop itself, so that it can be scrolled with the keyboard ([ScrollPaneTabStop]); it checks
/// whenever it or its content changes size. Give it a name (`ariaLabel`) where it may be one.
public class ScrollPane extends ScrollPanel {

    private static final String BLOCK = "scroll-pane";
    // What takes the focus by tabbing to it
    private static final String FOCUSABLE = "a[href], button, input, select, textarea, iframe, "
            + "[contenteditable=''], [contenteditable='true'], [tabindex]:not([tabindex='-1'])";

    // Whether the pane was made a tab stop or taken out of the tab order (setFocusable), or null to
    // decide from its content
    private Boolean chosenTabStop;
    // The content's element, watched for changes of size while the pane is attached
    private Element watchedContent;

    /// Makes an empty pane.
    public ScrollPane() {
        addStyleName(BLOCK);
        getContainerElement().addClassName(BLOCK + "__content");
    }

    @Override
    public void setWidget(final Widget widget) {
        super.setWidget(widget);
        if (isAttached()) {
            watchContent();
            updateTabStop();
        }
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        GlobalResizeObserver.addListener(getElement(), element -> updateTabStop());
        watchContent();
        updateTabStop();
    }

    @Override
    protected void onUnload() {
        GlobalResizeObserver.removeListener(getElement());
        unwatchContent();
        super.onUnload();
    }

    /// @param role The pane's ARIA role, e.g. `region` for content that is named and can be
    ///             scrolled with the keyboard.
    public void setRole(final String role) {
        getElement().setAttribute("role", role);
    }

    /// @param ariaLabel What the pane holds, for screen readers.
    public void setAriaLabel(final String ariaLabel) {
        getElement().setAttribute("aria-label", ariaLabel);
    }

    /// @param fitContent Whether the pane is sized by its own classes or its content, rather than
    ///                   filling its parent.
    public void setFitContent(final boolean fitContent) {
        setStyleName("scroll-pane--fit", fitContent);
    }

    /// Makes the pane a tab stop whatever its content, or takes it out of the tab order, rather
    /// than it being one only while its content overflows with nothing focusable in it.
    ///
    /// @param focusable Whether the pane is a tab stop.
    public void setFocusable(final boolean focusable) {
        chosenTabStop = focusable;
        updateTabStop();
    }

    private void watchContent() {
        unwatchContent();
        final Widget widget = getWidget();
        if (widget != null) {
            watchedContent = widget.getElement();
            GlobalResizeObserver.addListener(watchedContent, element -> updateTabStop());
        }
    }

    private void unwatchContent() {
        if (watchedContent != null) {
            GlobalResizeObserver.removeListener(watchedContent);
            watchedContent = null;
        }
    }

    private void updateTabStop() {
        final Element element = getElement();
        final boolean overflows = element.getScrollHeight() > element.getClientHeight()
                || element.getScrollWidth() > element.getClientWidth();
        final boolean tabStop = ScrollPaneTabStop.isTabStop(chosenTabStop,
                overflows,
                holdsFocusable(element, FOCUSABLE));
        if (tabStop) {
            element.setTabIndex(0);
        } else {
            element.removeAttribute("tabindex");
        }
    }

    private static native boolean holdsFocusable(final Element element, final String selector) /*-{
        return element.querySelector(selector) !== null;
    }-*/;
}
