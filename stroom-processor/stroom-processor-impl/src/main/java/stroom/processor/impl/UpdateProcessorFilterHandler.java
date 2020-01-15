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

import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.UpdateProcessorFilterAction;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

public class UpdateProcessorFilterHandler extends AbstractTaskHandler<UpdateProcessorFilterAction, ProcessorFilter> {
    private final ProcessorFilterService processorFilterService;
    private final SecurityContext securityContext;

    @Inject
    UpdateProcessorFilterHandler(final ProcessorFilterService processorFilterService,
                                 final SecurityContext securityContext) {
        this.processorFilterService = processorFilterService;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorFilter exec(final UpdateProcessorFilterAction action) {
        // If the user doesn't have update permissions on the pipeline then return null.
        if (!securityContext.hasDocumentPermission(PipelineDoc.DOCUMENT_TYPE, action.getProcessorFilter().getProcessor().getPipelineUuid(), DocumentPermissionNames.UPDATE)) {
            throw new PermissionException("You do not have permission to update this processor filter");
        }

        return securityContext.secureResult(PermissionNames.MANAGE_PROCESSORS_PERMISSION, () ->
                securityContext.useAsReadResult(() ->
                        processorFilterService.update(action.getProcessorFilter())));
    }
}
