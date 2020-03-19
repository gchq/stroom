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

package stroom.security.impl.session;

import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.List;

class SessionListClusterHandler extends AbstractTaskHandler<SessionListClusterTask, List<SessionDetails>> {
    private final SessionListService sessionListService;
    private final SecurityContext securityContext;

    @Inject
    SessionListClusterHandler(final SessionListService sessionListService,
                              final SecurityContext securityContext) {
        this.sessionListService = sessionListService;
        this.securityContext = securityContext;
    }

    @Override
    public List<SessionDetails> exec(final SessionListClusterTask task) {
        return securityContext.insecureResult(sessionListService::list);
    }
}
