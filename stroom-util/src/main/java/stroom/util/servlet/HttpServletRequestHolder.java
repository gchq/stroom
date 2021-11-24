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

package stroom.util.servlet;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Singleton
public class HttpServletRequestHolder implements Provider<HttpServletRequest> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpServletRequestHolder.class);

    private final ThreadLocal<HttpServletRequest> threadLocal = new InheritableThreadLocal<>();

    @Override
    public HttpServletRequest get() {
        return threadLocal.get();
    }

    public void set(final HttpServletRequest httpServletRequest) {

        if (LOGGER.isDebugEnabled()) {
            if (httpServletRequest == null) {
                LOGGER.debug(() ->
                        LogUtil.message("Clearing held request against thread {}",
                                Thread.currentThread().getId()));
            } else {
                LOGGER.debug(() ->
                        LogUtil.message("Holding request with session id {} against thread {}",
                                NullSafe.get(
                                        httpServletRequest,
                                        request -> request.getSession(false),
                                        HttpSession::getId),
                                Thread.currentThread().getId()));
            }
        }

        threadLocal.set(httpServletRequest);
    }
}
