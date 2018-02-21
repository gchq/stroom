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

package stroom.dispatch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.dispatch.server.DispatchServiceImpl;
import stroom.dispatch.shared.DispatchService;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.task.server.TaskHandlerBeanRegistry;
import stroom.task.server.TaskManager;

@Configuration
public class DispatchSpringConfig {
    @Bean(DispatchServiceImpl.BEAN_NAME)
    public DispatchService dispatchService(final TaskHandlerBeanRegistry taskHandlerBeanRegistry, final TaskManager taskManager,
                                           final SecurityContext securityContext, final HttpServletRequestHolder httpServletRequestHolder) {
        return new DispatchServiceImpl(taskHandlerBeanRegistry, taskManager, securityContext, httpServletRequestHolder);
    }
}