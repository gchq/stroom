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

package stroom.entity.client.presenter;

import stroom.ai.client.ChatLinks;
import stroom.data.client.presenter.DataViewType;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.pipeline.shared.SourceLocation;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;

/// Opens the links clicked in rendered markdown (e.g. a Documentation document), which is shown in a sandboxed
/// frame where scripts can't run: a link followed in the frame would load Stroom, or another site, in it.
public final class MarkdownLinks {

    private MarkdownLinks() {
        // Static only.
    }

    /// @param source Where the events to open things come from.
    /// @param href   The link clicked, as the browser resolves it (absolute).
    /// @return True if the link was opened: a link to a document or a stream in this Stroom is opened in it, in a
    /// tab of its own, and another web or mail link in a new browser tab. False for anything else (e.g. a link
    /// to a place in the page), which the frame follows as usual.
    public static boolean open(final HasHandlers source, final String href) {
        if (isThisStroom(href)) {
            final DocRef docRef = ChatLinks.openDocTarget(href);
            if (docRef != null) {
                OpenDocumentEvent.fire(source, docRef, true);
                return true;
            }
            final ChatLinks.StreamTarget stream = ChatLinks.openStreamTarget(href);
            if (stream != null) {
                ShowDataEvent.fire(source,
                        SourceLocation.builder(stream.streamId())
                                .withPartIndex(0L)
                                .withRecordIndex(stream.recordNo() == null
                                        ? 0L
                                        : stream.recordNo() - 1)
                                .build(),
                        DataViewType.PREVIEW,
                        DisplayMode.STROOM_TAB);
                return true;
            }
        }
        if (ChatLinks.isExternal(href)) {
            Window.open(href.trim(), "_blank", "noopener,noreferrer");
            return true;
        }
        return false;
    }

    /// @return True if the link is to this Stroom, rather than to a link of the same shape on another site.
    static boolean isThisStroom(final String href) {
        return href != null && href.startsWith(Window.Location.getProtocol() + "//" + Window.Location.getHost() + "/");
    }
}
