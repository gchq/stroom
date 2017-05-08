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

package stroom.node.server;

import org.springframework.context.annotation.Scope;
import stroom.node.shared.ClientProperties;
import stroom.node.shared.ClientPropertiesService;
import stroom.node.shared.FetchClientPropertiesAction;
import stroom.security.Insecure;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchClientPropertiesAction.class)
@Scope(value = StroomScope.TASK)
@Insecure
public class FetchClientPropertiesHandler extends AbstractTaskHandler<FetchClientPropertiesAction, ClientProperties> {
    private final ClientPropertiesService clientPropertiesService;

    @Inject
    FetchClientPropertiesHandler(final ClientPropertiesService clientPropertiesService) {
        this.clientPropertiesService = clientPropertiesService;
    }

    @Override
    public ClientProperties exec(final FetchClientPropertiesAction action) {
        return clientPropertiesService.getProperties();
    }
}
