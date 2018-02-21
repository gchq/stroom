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

package stroom.explorer;

import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.FetchDocumentTypesAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDocumentTypesAction.class)
class FetchDocumentTypesHandler extends AbstractTaskHandler<FetchDocumentTypesAction, DocumentTypes> {
    private final ExplorerService explorerService;

    @Inject
    FetchDocumentTypesHandler(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    @Override
    public DocumentTypes exec(final FetchDocumentTypesAction action) {
        return explorerService.getDocumentTypes();
    }
}
