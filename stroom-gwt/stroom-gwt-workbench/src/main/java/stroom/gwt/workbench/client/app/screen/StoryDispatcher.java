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

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import com.google.inject.Inject;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

import java.util.Objects;

/// The RestyGWT [Dispatcher] of a harness's [ScreenGinjector], bound in place of Stroom's
/// network `RestDispatcher`, so that the injector's `RestFactory` sends every request to it. It
/// passes each request on to the harness's own dispatcher (its `FixtureDispatcher`), which the
/// harness [sets][#setDelegate(Dispatcher)] as soon as it has created the injector (GIN can't
/// create the fixture dispatcher, as that needs the story's fixtures).
///
/// Each injector has its own, so the requests of each harness go to its own fixtures, and a
/// presenter of an old rendering, which keeps its injector's `RestFactory`, can only reach that
/// rendering's (disposed) dispatcher.
public final class StoryDispatcher implements Dispatcher {

    private Dispatcher delegate;

    /// Creates a dispatcher that refuses requests until [#setDelegate(Dispatcher)] is called.
    @Inject
    public StoryDispatcher() {
        super();
    }

    /// @param delegate The dispatcher to send every request to.
    public void setDelegate(final Dispatcher delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    /// @return True once [#setDelegate(Dispatcher)] has been called, i.e. once a harness has
    /// used this dispatcher's injector.
    public boolean hasDelegate() {
        return delegate != null;
    }

    /// Sends the request with the delegate.
    ///
    /// @param method  RestyGWT's method.
    /// @param builder The request.
    /// @return The delegate's request.
    /// @throws RequestException If the delegate has not been set, or the delegate fails.
    @Override
    public Request send(final Method method, final RequestBuilder builder) throws RequestException {
        if (delegate == null) {
            throw new RequestException("The screen harness has not set its dispatcher, so "
                    + builder.getHTTPMethod() + " " + builder.getUrl() + " can't be answered");
        }
        return delegate.send(method, builder);
    }
}
