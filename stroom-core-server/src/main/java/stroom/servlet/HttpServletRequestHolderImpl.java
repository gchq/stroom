/*
 * Copyright 2017 Crown Copyright
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

package stroom.servlet;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Component
public class HttpServletRequestHolderImpl implements HttpServletRequestHolder {
    private final ThreadLocal<HttpServletRequest> threadLocal = new InheritableThreadLocal<>();

    @Override
    public HttpServletRequest get() {
        return threadLocal.get();
    }

    @Override
    public void set(final HttpServletRequest httpServletRequest) {
        threadLocal.set(httpServletRequest);
    }

    @Override
    public String toString() {
        return getSessionId();
    }

    /**
     * @return back the session id without creating a session
     */
    @Override
    public String getSessionId() {
        final HttpServletRequest request = get();
        if (request != null) {
            final HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                return httpSession.getId();
            }
        }

        return null;
    }
}
