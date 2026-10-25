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

package stroom.iframe.client.presenter;

import stroom.iframe.client.component.IFrame;
import stroom.iframe.client.component.IFrame.SandboxOption;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Shows a page in an [IFrame], e.g. a link's page as a dialog or a document's Documentation tab,
/// and follows the page's title, which names the dialog or tab. A thin presenter over the
/// component, kept while a dialog that renames itself (`RenamePopupEvent`) needs one.
public class IFramePresenter extends MyPresenterWidget<View> {

    private static final String DEFAULT_TITLE = "Loading...";

    private final IFrame frame;
    private Runnable onTitleChange = () -> {
    };
    private boolean updateTitle = true;
    private String lastTitle = DEFAULT_TITLE;
    private String customTitle;

    @Inject
    public IFramePresenter(final EventBus eventBus, final IFrame frame) {
        super(eventBus, frame::asWidget);
        this.frame = frame;
        // Reads the page's title every second until it is closed (or given a title of its own)
        Scheduler.get().scheduleFixedDelay(this::readTitle, 1000);
        // Stops the poll, which would otherwise run, and keep this, for the life of the page (e.g. a
        // closed document's Documentation tab)
        onDispose(this::close);
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

    /// @param id The frame's element id.
    public void setId(final String id) {
        frame.setId(id);
    }

    /// Shows the page at a URL, whose address is its label until it has a title.
    ///
    /// @param url The page's address.
    public void setUrl(final String url) {
        lastTitle = url;
        frame.setUrl(url);
    }

    /// Shows a page of HTML rather than one at a URL.
    ///
    /// @param html The page.
    public void setSrcDoc(final String html) {
        frame.setSrcDoc(html);
    }

    /// @param isEnabled      Whether the page is sandboxed.
    /// @param sandboxOptions What a sandboxed page may still do.
    public void setSandboxEnabled(final boolean isEnabled, final SandboxOption... sandboxOptions) {
        frame.setSandboxEnabled(isEnabled, sandboxOptions);
    }

    /// Gives the page a label of its own, which the page's title then doesn't change.
    ///
    /// @param customTitle The label, e.g. a document's name.
    public void setCustomTitle(final String customTitle) {
        this.customTitle = customTitle;
    }

    /// Names the frame for assistive technology, which announces a frame by its `title` (WCAG
    /// 4.1.2). Not the same as [#setCustomTitle(String)], which is the title of the tab showing it.
    ///
    /// @param name The frame's name, e.g. `Preview`.
    public void setAccessibleName(final String name) {
        frame.setAccessibleName(name);
    }

    /// @return The label for the tab showing the page: its own label, else its title, else its
    ///         address.
    public String getLabel() {
        return customTitle != null
                ? customTitle
                : lastTitle;
    }

    /// Stops reading the page's title.
    public void close() {
        updateTitle = false;
    }

    /// @param onTitleChange What to run when the page shown changes its title.
    public void setOnTitleChange(final Runnable onTitleChange) {
        this.onTitleChange = onTitleChange;
    }
}
