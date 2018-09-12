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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.stereotype.Component;
import stroom.activity.shared.Activity;
import stroom.servlet.HttpServletRequestHolder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Component
public class CurrentActivity implements BeanFactoryAware {
    private static final String NAME = "SESSION_ACTIVITY";
    private BeanFactory beanFactory;

    public Activity getActivity() {
        Activity activity = null;

        final HttpServletRequest request = getRequest();
        if (request != null) {
            final HttpSession session = request.getSession();
            final Object object = session.getAttribute(NAME);
            if (object instanceof Activity) {
                activity = (Activity) object;
            }
        }

        return activity;
    }

    public void setActivity(final Activity activity) {
        final HttpServletRequest request = getRequest();
        if (request != null) {
            final HttpSession session = request.getSession();
            session.setAttribute(NAME, activity);
        }
    }

    private HttpServletRequest getRequest() {
        if (beanFactory != null) {
            try {
                final HttpServletRequestHolder holder = beanFactory.getBean(HttpServletRequestHolder.class);
                if (holder != null) {
                    return holder.get();
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }
        return null;
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}

