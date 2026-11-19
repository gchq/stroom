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

import stroom.dispatch.client.RestFactory;
import stroom.dispatch.client.RestFactoryImpl;
import stroom.gwt.workbench.client.app.rest.FixtureDispatcher;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.TimerReplyScheduler;

import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Defaults;
import org.fusesource.restygwt.client.Dispatcher;

/// Creates Stroom's real [RestFactory], `RestFactoryImpl`, with a given dispatcher, which it uses
/// for every request it sends.
///
/// It is created directly rather than with Stroom's `RestModule`, as that binds its `Dispatcher` to
/// Stroom's network `RestDispatcher` (and GIN can't override a binding).
///
/// `RestFactoryImpl`'s constructor also sets RestyGWT's static service root (the same for every
/// harness) and static default dispatcher. Stroom only sends requests through `RestFactory`, which
/// uses its own dispatcher, so nothing should use the static default; to be safe it is set to a
/// dispatcher that drops every request (and says so in the console), so that a request that
/// bypasses `RestFactory` neither reaches the network nor some other harness's fixtures.
final class StroomRestFactory {

    // RestyGWT's static default dispatcher while screen stories run: it drops every request
    private static final Dispatcher STATIC_DEFAULT_DISPATCHER = createStaticDefaultDispatcher();

    private StroomRestFactory() {
        // Static methods only
    }

    /// @param eventBus   The event bus it fires its events on, e.g. alerts of failed requests.
    /// @param dispatcher The dispatcher it sends every request with.
    /// @return Stroom's real REST factory.
    static RestFactory create(final EventBus eventBus, final Dispatcher dispatcher) {
        final RestFactory restFactory = new RestFactoryImpl(eventBus, dispatcher);
        Defaults.setDispatcher(STATIC_DEFAULT_DISPATCHER);
        return restFactory;
    }

    private static Dispatcher createStaticDefaultDispatcher() {
        final FixtureDispatcher dispatcher = new FixtureDispatcher(RestFixtures.none(),
                Defaults::getServiceRoot, new TimerReplyScheduler(), new StaticDefaultListener());
        // A disposed dispatcher drops every request
        dispatcher.dispose();
        return dispatcher;
    }

    private static native void consoleWarn(String message) /*-{
        if ($wnd.console && $wnd.console.warn) {
            $wnd.console.warn(message);
        }
    }-*/;

    // --------------------------------------------------------------------------------


    /// Reports the requests that reach RestyGWT's static default dispatcher, which are dropped.
    private static final class StaticDefaultListener implements FixtureDispatcher.Listener {

        @Override
        public void onRequest(final RecordedRequest request) {
            // Never called: the dispatcher is disposed, so drops every request
        }

        @Override
        public void onUnhandledRequest(final RecordedRequest request, final String message, final boolean strict) {
            // Never called, as above
        }

        @Override
        public void onFixtureError(final RecordedRequest request, final String message) {
            // Never called, as above
        }

        @Override
        public void onDroppedRequest(final RecordedRequest request) {
            consoleWarn("A request that bypassed Stroom's RestFactory (so used RestyGWT's static default "
                    + "dispatcher) was dropped: " + request.describe());
        }
    }
}
