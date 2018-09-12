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

package stroom.logging;

import org.springframework.stereotype.Component;
import stroom.activity.shared.Activity;
import stroom.servlet.HttpServletRequestHolder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Component
public class CurrentActivity {
    private static final String NAME = "SESSION_ACTIVITY";
    private final HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    public CurrentActivity(final HttpServletRequestHolder httpServletRequestHolder) {
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    public Activity getActivity() {
        final HttpServletRequest request = httpServletRequestHolder.get();
        if (request == null) {
            throw new NullPointerException("Request holder has no current request");
        }

        final HttpSession session = request.getSession();
        final Object object = session.getAttribute(NAME);
        if (!(object instanceof Activity)) {
            return null;
        }

        return (Activity) object;
    }

    public void setActivity(final Activity activity) {
        final HttpServletRequest request = httpServletRequestHolder.get();
        if (request == null) {
            throw new NullPointerException("Request holder has no current request");
        }

        final HttpSession session = request.getSession();
        session.setAttribute(NAME, activity);
    }
}

