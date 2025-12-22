/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.servlet;

import stroom.util.servlet.SessionIdProvider;
import stroom.util.servlet.SessionUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;

class SessionIdProviderImpl implements SessionIdProvider {

    private final Provider<HttpServletRequest> httpServletRequestProvider;

    @Inject
    SessionIdProviderImpl(final Provider<HttpServletRequest> httpServletRequestProvider) {
        this.httpServletRequestProvider = httpServletRequestProvider;
    }

    /**
     * @return back the session id without creating a session
     */
    @Override
    public String get() {
        return SessionUtil.getSessionId(httpServletRequestProvider.get());
    }
}
