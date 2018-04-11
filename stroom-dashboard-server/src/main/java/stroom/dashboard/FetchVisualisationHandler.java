/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard;

import stroom.dashboard.shared.FetchVisualisationAction;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.visualisation.VisualisationService;
import stroom.visualisation.shared.Visualisation;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchVisualisationAction.class)
class FetchVisualisationHandler extends AbstractTaskHandler<FetchVisualisationAction, Visualisation> {
    private final VisualisationService visualisationService;
    private final Security security;

    @Inject
    FetchVisualisationHandler(final VisualisationService visualisationService,
                              final Security security) {
        this.visualisationService = visualisationService;
        this.security = security;
    }

    @Override
    public Visualisation exec(final FetchVisualisationAction action) {
        return security.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the visualisation if they have 'use' permission.
            return security.useAsReadResult(() -> visualisationService.loadByUuid(action.getVisualisation().getUuid()));
        });
    }
}
