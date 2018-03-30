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

package stroom.dictionary;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DownloadDictionaryAction;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.logging.DocumentEventLog;
import stroom.resource.ResourceStore;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@TaskHandlerBean(task = DownloadDictionaryAction.class)
class DownloadDictionaryHandler extends AbstractTaskHandler<DownloadDictionaryAction, ResourceGeneration> {
    private final ResourceStore resourceStore;
    private final DocumentEventLog documentEventLog;
    private final DictionaryStore dictionaryStore;
    private final Security security;

    @Inject
    DownloadDictionaryHandler(final ResourceStore resourceStore,
                              final DocumentEventLog documentEventLog,
                              final DictionaryStore dictionaryStore,
                              final Security security) {
        this.resourceStore = resourceStore;
        this.documentEventLog = documentEventLog;
        this.dictionaryStore = dictionaryStore;
        this.security = security;
    }

    @Override
    public ResourceGeneration exec(final DownloadDictionaryAction action) {
        return security.secureResult(() -> {
            // Get dictionary.
            final DictionaryDoc dictionary = dictionaryStore.read(action.getUuid());
            if (dictionary == null) {
                throw new EntityServiceException("Unable to find dictionary");
            }

            try {
                final ResourceKey resourceKey = resourceStore.createTempFile("dictionary.txt");
                final Path file = resourceStore.getTempFile(resourceKey);
                Files.write(file, dictionary.getData().getBytes(StreamUtil.DEFAULT_CHARSET));
                documentEventLog.download(dictionary, null);
                return new ResourceGeneration(resourceKey, new ArrayList<>());

            } catch (final IOException e) {
                documentEventLog.download(dictionary, null);
                throw EntityServiceExceptionUtil.create(e);
            }
        });
    }
}
