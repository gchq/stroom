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

package stroom.widget.datagrid.client;

import stroom.ai.shared.AskStroomAiContext;
import stroom.widget.util.client.DomRequests;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;

/// Asks Stroom AI about a table's data from a widget that has no event bus to fire an
/// [AskStroomAiEvent] on (a data grid's context menu). The widget makes a request with [#fire],
/// which bubbles up the page to the one relay installed for the page ([#relay], by Stroom's core
/// controller), which fires the [AskStroomAiEvent] on the event bus. (Not `AskStroomAiRequest`, the
/// name of the server's request.)
public final class AskStroomAiTableRequest {

    private static final String EVENT = "stroom-ask-stroom-ai-table-request";

    private AskStroomAiTableRequest() {
        // Static utility
    }

    /// Asks Stroom AI about some of a table's data.
    ///
    /// @param source  The table's element, which the request bubbles up from.
    /// @param context What to ask about: the table's description, headers and rows.
    public static void fire(final Element source, final AskStroomAiContext context) {
        DomRequests.send(source, EVENT, context);
    }

    /// Asks Stroom AI what widgets on the page ask, by firing an [AskStroomAiEvent] for each.
    ///
    /// @param handlers What the [AskStroomAiEvent]s are fired on, e.g. a controller, or an event bus's
    ///                 `fireEvent`.
    /// @return The registration that stops the relay.
    public static HandlerRegistration relay(final HasHandlers handlers) {
        return DomRequests.<AskStroomAiContext>receive(EVENT, context -> AskStroomAiEvent.fire(handlers, context));
    }
}
