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

package stroom.util.servlet;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Holds the current thread's {@link HttpServletResponse}, mirroring
 * {@link HttpServletRequestHolder}, for resources whose interface cannot carry servlet
 * {@code @Context} parameters (e.g. GWT-shared REST interfaces) but that need to set response
 * headers such as {@code Set-Cookie}.
 */
@Singleton
public class HttpServletResponseHolder implements Provider<HttpServletResponse> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpServletResponseHolder.class);

    private final ThreadLocal<HttpServletResponse> threadLocal = new InheritableThreadLocal<>();

    @Override
    public HttpServletResponse get() {
        return threadLocal.get();
    }

    public void set(final HttpServletResponse httpServletResponse) {
        LOGGER.debug(() -> LogUtil.message("{} held response against thread {}",
                httpServletResponse == null
                        ? "Clearing"
                        : "Holding",
                Thread.currentThread().threadId()));
        threadLocal.set(httpServletResponse);
    }
}
