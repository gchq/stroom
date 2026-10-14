/*
 * Copyright 2024 Crown Copyright
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

package stroom.data.pager.client;

import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/// A toolbar's refresh button, which also shows whether what it refreshes is refreshing (a turning
/// spinner) and, if it can be paused (e.g. a dashboard's table or visualisation), whether updates are
/// paused. It is a [ToolbarButton], so it is one of its toolbar's buttons like any other: made by
/// [stroom.widget.button.client.Toolbar#refresh], or declared in a template inside a toolbar as
/// `<refresh-button:RefreshButton ui:field="refresh"/>`.
public class RefreshButton
        extends ToolbarButton
        implements TaskMonitorFactory {

    // Its BEM names (see refresh-button.css)
    private static final String BLOCK = "refresh-button";
    private static final String ICON = BLOCK + "__icon";
    private static final String REFRESHING = BLOCK + "--refreshing";
    private static final String PAUSABLE = BLOCK + "--pausable";
    private static final String PAUSED = BLOCK + "--paused";

    private int taskCount;
    private boolean allowPause;
    private boolean paused;
    private boolean refreshing;
    private boolean refreshState;

    /// A button holding four icons, one above another, whose modifiers (refreshing, pausable and
    /// paused) choose which are shown (see refresh-button.css): refresh; a spinner turning; a static
    /// circle; and pause.
    public RefreshButton() {
        addStyleName(BLOCK);
        setTitle("Refresh");

        final Element element = getElement();
        element.appendChild(icon(SvgImage.REFRESH, "refresh"));
        element.appendChild(icon(SvgImage.SPINNER_CIRCLE, "circle"));
        element.appendChild(icon(SvgImage.SPINNER_SPINNING, "spinning"));
        element.appendChild(icon(SvgImage.SPINNER_PAUSE, "pause"));
    }

    // The image's SVG element, with the classes that place and show it, e.g. refresh-button__icon
    // and refresh-button__icon--spinning
    private static Element icon(final SvgImage image, final String name) {
        final Element holder = Document.get().createDivElement();
        holder.setInnerHTML(image.getSvg());
        final Element svg = holder.getFirstChildElement();
        // An SVG element's className isn't a string, so set the class attribute itself
        final String classes = svg.getAttribute("class");
        svg.setAttribute("class", (classes == null || classes.isEmpty()
                ? ""
                : classes + " ") + ICON + " " + ICON + "--" + name);
        // The icon is decoration: the button is named by its title
        svg.setAttribute("aria-hidden", "true");
        return svg;
    }

    /// @param refreshing Whether what the button refreshes is refreshing, shown by a turning spinner.
    public void setRefreshing(final boolean refreshing) {
        this.refreshing = refreshing;
        updateRefreshState();
    }

    /// Makes the button pause and resume updates rather than refresh: it is named for what pressing
    /// it does, and can only be pressed while refreshing or paused.
    ///
    /// @param allowPause Whether the button pauses and resumes updates.
    public void setAllowPause(final boolean allowPause) {
        this.allowPause = allowPause;
        setStyleName(PAUSABLE, allowPause);
        if (allowPause) {
            setEnabled(false);
        }
        update();
    }

    /// @param paused Whether updates are paused, shown by a pulsing pause icon.
    public void setPaused(final boolean paused) {
        this.paused = paused;
        setStyleName(PAUSED, paused);
        update();
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                updateRefreshState();
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;
                updateRefreshState();
            }
        };
    }

    /// Shows whether what the button refreshes is refreshing, from [#setRefreshing(boolean)] and the
    /// tasks started by its task monitors.
    public void updateRefreshState() {
        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        final boolean refreshState = refreshing || taskCount > 0;
        if (refreshState != this.refreshState) {
            this.refreshState = refreshState;
            setStyleName(REFRESHING, refreshState);
        }
        update();
    }

    private void update() {
        if (allowPause) {
            setEnabled(paused || refreshing || taskCount > 0);

            if (paused) {
                setTitle("Resume Update");
            } else if (refreshing || taskCount > 0) {
                setTitle("Pause Update");
            } else {
                setTitle("Not Updating");
            }
        } else {
            setTitle("Refresh");
        }
    }
}
