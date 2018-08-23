/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.importexport;

import stroom.entity.shared.ResultList;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.FetchDependenciesAction;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDependenciesAction.class)
class FetchDependenciesHandler extends AbstractTaskHandler<FetchDependenciesAction, ResultList<Dependency>> {
    private final DependencyService dependencyService;
    private final Security security;

    @Inject
    FetchDependenciesHandler(final DependencyService dependencyService,
                             final Security security) {
        this.dependencyService = dependencyService;
        this.security = security;
    }

    @Override
    public ResultList<Dependency> exec(final FetchDependenciesAction task) {
        return security.secureResult(() -> dependencyService.getDependencies(task.getCriteria()));
    }
}