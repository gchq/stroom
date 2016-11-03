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

package stroom.entity.server;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.google.web.bindery.requestfactory.shared.ServiceLocator;

@Component
public class SpringServiceLocator implements ServiceLocator {
    @Override
    public Object getInstance(final Class<?> clazz) {
        final ApplicationContext context = WebApplicationContextUtils
                .getWebApplicationContext(SpringRequestFactoryServlet.getThreadLocalContext());
        return context.getBean(clazz);
    }
}
