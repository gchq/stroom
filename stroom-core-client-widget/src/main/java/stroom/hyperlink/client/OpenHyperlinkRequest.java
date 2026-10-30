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

package stroom.hyperlink.client;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.util.client.DomRequests;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

/// Opens a link from a widget that has no event bus to fire a [HyperlinkEvent] on (e.g. a data
/// grid's context menu). The widget makes a request with [#fire], which bubbles up the page to the
/// one relay installed for the page ([#relay], by Stroom's core controller), which fires the
/// [HyperlinkEvent] on the event bus.
public final class OpenHyperlinkRequest {

    private static final String EVENT = "stroom-open-hyperlink-request";

    private OpenHyperlinkRequest() {
        // Static utility
    }

    /// Asks for a link to be opened.
    ///
    /// @param source    The element the link is in, which the request bubbles up from.
    /// @param hyperlink The link.
    public static void fire(final Element source, final LinkValue hyperlink) {
        DomRequests.send(source, EVENT, hyperlink);
    }

    /// Opens the links that widgets on the page ask for, by firing a [HyperlinkEvent] for each,
    /// whose work is shown as the app's (a [DefaultTaskMonitorFactory] on the same handlers).
    ///
    /// @param handlers What the [HyperlinkEvent]s are fired on, e.g. a controller, or an event bus's
    ///                 `fireEvent`.
    /// @return The registration that stops the relay.
    public static HandlerRegistration relay(final HasHandlers handlers) {
        return DomRequests.<LinkValue>receive(EVENT, hyperlink ->
                HyperlinkEvent.fire(handlers, hyperlink, new DefaultTaskMonitorFactory(handlers)));
    }
}
