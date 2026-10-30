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

package stroom.iframe.client.component;

import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Shows a page in an [IFrame], e.g. a link's page as a dialog, and follows the page's title, which
/// names the dialog.
public class IFrameController extends Controller {

    private static final String DEFAULT_TITLE = "Loading...";

    private final IFrame frame;
    private Runnable onTitleChange = () -> {
    };
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;

    /// @param eventBus The app's event bus, which the popup manager listens to.
    /// @param frame    The frame.
    @Inject
    public IFrameController(final EventBus eventBus, final IFrame frame) {
        super(eventBus);
        this.frame = frame;
        initWidget(frame);
        // Reads the page's title every second until it is disposed (or given a title of its own)
        Scheduler.get().scheduleFixedDelay(this::readTitle, 1000);
        // Stops the poll, which would otherwise run, and keep this, for the life of the page
        onDispose(() -> updateTitle = false);
    }

    // Tells the owner when the page's title changes; false once the poll is no longer wanted
    private boolean readTitle() {
        final String title = frame.getPageTitle();
        if (title != null && !title.trim().isEmpty() && !Objects.equals(lastTitle, title)) {
            lastTitle = title;
            onTitleChange.run();
        }
        return updateTitle && customTitle == null;
    }

    /// Shows the page at a URL, whose address is its label until it has a title.
    ///
    /// @param url The page's address.
    public void setUrl(final String url) {
        lastTitle = url;
        frame.setUrl(url);
    }

    /// Gives the page a label of its own, which the page's title then doesn't change.
    ///
    /// @param customTitle The label, or null to follow the page's title.
    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
    }

    /// @return The label for what shows the page: its own label, else its title, else its address.
    public String getLabel() {
        return customTitle != null
                ? customTitle
                : lastTitle;
    }

    /// @param onTitleChange What to run when the page shown changes its title.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = onTitleChange;
    }
}
