/*
 * Copyright 2018 Crown Copyright
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

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class CurrentActivityImpl implements CurrentActivity {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentActivity.class);

    private static final String NAME = "SESSION_ACTIVITY";
    private final Provider<HttpServletRequest> httpServletRequestProvider;

    @Inject
    CurrentActivityImpl(final Provider<HttpServletRequest> httpServletRequestProvider) {
        this.httpServletRequestProvider = httpServletRequestProvider;
    }

    public Activity getActivity() {
        Activity activity = null;

        try {
            final HttpServletRequest request = httpServletRequestProvider.get();
            if (request != null) {
                final HttpSession session = request.getSession();
                final Object object = session.getAttribute(NAME);
                if (object instanceof Activity) {
                    activity = (Activity) object;
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return activity;
    }

    public void setActivity(final Activity activity) {
        final HttpServletRequest request = httpServletRequestProvider.get();
        if (request != null) {
            final HttpSession session = request.getSession();
            session.setAttribute(NAME, activity);
        }
    }
}

