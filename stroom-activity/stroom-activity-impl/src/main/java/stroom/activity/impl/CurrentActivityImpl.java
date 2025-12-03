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

package stroom.activity.impl;

import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.Activity;
import stroom.security.api.SecurityContext;
import stroom.util.servlet.SessionUtil;

import com.google.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CurrentActivityImpl implements CurrentActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentActivity.class);

    private static final String NAME = "SESSION_ACTIVITY";
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final SecurityContext securityContext;

    @Inject
    CurrentActivityImpl(final Provider<HttpServletRequest> httpServletRequestProvider,
                        final SecurityContext securityContext) {
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Activity getActivity() {
        return securityContext.secureResult(() -> {
            Activity activity = null;

            try {
                final HttpServletRequest request = httpServletRequestProvider.get();
                activity = SessionUtil.getAttribute(request, NAME, Activity.class, false);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }

            return activity;
        });
    }

    @Override
    public void setActivity(final Activity activity) {
        securityContext.secure(() -> {
            final HttpServletRequest request = httpServletRequestProvider.get();
            SessionUtil.setAttribute(request, NAME, activity, false);
        });
    }
}

