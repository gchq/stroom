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

package stroom.gwt.workbench.client.app.screen;

import stroom.core.client.HasSaveRegistry;
import stroom.core.client.LocationManager;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The [LocationManager] of a screen story's harness, a singleton of its [ScreenGinjector]. Stroom
/// downloads files (e.g. with `ExportFileCompleteUtil`) by replacing the page's location with the
/// file's URL, which would navigate the story away; this records the URL instead, so that a play
/// can check that a download happened with the harness's `ScreenHarness.DOWNLOAD_SPY`.
///
/// Like Stroom's, it adds a window-closing handler when created, which the harness removes with
/// [#removeWindowClosingHandler()] when disposed.
public final class StoryLocationManager extends LocationManager {

    private Consumer<String> downloadListener = url -> {
    };

    /// @param eventBus The harness's event bus, which events such as Stroom's `WindowCloseEvent`
    ///                 are fired on.
    @Inject
    public StoryLocationManager(final EventBus eventBus) {
        // No documents are ever registered as unsaved, so closing the window never asks
        super(eventBus, new HasSaveRegistry());
    }

    /// Records the URL instead of navigating to it.
    ///
    /// @param newURL The URL, e.g. `http://localhost:6008/resourcestore/my-notes.md?uuid=k1`.
    @Override
    public void replace(final String newURL) {
        downloadListener.accept(relativeTo(newURL, GWT.getHostPageBaseURL()));
    }

    /// @param downloadListener Told the URL of each download, relative to the host page, e.g.
    ///                         `resourcestore/my-notes.md?uuid=k1`.
    public void setDownloadListener(final Consumer<String> downloadListener) {
        this.downloadListener = Objects.requireNonNull(downloadListener);
    }

    /// @param url  A URL, or null.
    /// @param base The host page's base URL, or null.
    /// @return The URL without the base at its start, or the URL as given if it doesn't start
    /// with the base.
    static String relativeTo(final String url, final String base) {
        return url != null && base != null && url.startsWith(base)
                ? url.substring(base.length())
                : url;
    }
}
