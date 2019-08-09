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

package stroom.processor.impl;

import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessorFilterAction;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

public class CreateProcessorHandler extends AbstractTaskHandler<CreateProcessorFilterAction, ProcessorFilter> {
    private final ProcessorFilterService processorFilterService;
    private final SecurityContext securityContext;

    @Inject
    CreateProcessorHandler(final ProcessorFilterService processorFilterService,
                           final SecurityContext securityContext) {
        this.processorFilterService = processorFilterService;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorFilter exec(final CreateProcessorFilterAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () ->
                processorFilterService.create(action.getPipeline(), action.getQueryData(), action.getPriority(), action.isEnabled()));
    }
}
