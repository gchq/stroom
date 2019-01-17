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

package stroom.document;

import com.google.inject.AbstractModule;
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.task.api.TaskHandlerBinder;

public class DocumentModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DocumentService.class).to(DocumentServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(DocumentServiceReadAction.class, stroom.document.DocumentServiceReadHandler.class)
                .bind(DocumentServiceWriteAction.class, stroom.document.DocumentServiceWriteHandler.class);
    }
}