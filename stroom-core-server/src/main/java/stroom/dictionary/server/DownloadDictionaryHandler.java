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

package stroom.dictionary.server;

import org.springframework.context.annotation.Scope;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DownloadDictionaryAction;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.logging.DocumentEventLog;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@TaskHandlerBean(task = DownloadDictionaryAction.class)
@Scope(StroomScope.PROTOTYPE)
public class DownloadDictionaryHandler extends AbstractTaskHandler<DownloadDictionaryAction, ResourceGeneration> {
    private final SessionResourceStore sessionResourceStore;
    private final DocumentEventLog documentEventLog;
    private final DictionaryStore dictionaryStore;

    @Inject
    public DownloadDictionaryHandler(final SessionResourceStore sessionResourceStore,
                                     final DocumentEventLog documentEventLog,
                                     final DictionaryStore dictionaryStore) {
        this.sessionResourceStore = sessionResourceStore;
        this.documentEventLog = documentEventLog;
        this.dictionaryStore = dictionaryStore;
    }

    @Override
    public ResourceGeneration exec(final DownloadDictionaryAction action) {
        // Get dictionary.
        final DictionaryDoc dictionary = dictionaryStore.read(action.getUuid());
        if (dictionary == null) {
            throw new EntityServiceException("Unable to find dictionary");
        }

        try {
            final ResourceKey resourceKey = sessionResourceStore.createTempFile("dictionary.txt");
            final Path file = sessionResourceStore.getTempFile(resourceKey);
            Files.write(file, dictionary.getData().getBytes(StreamUtil.DEFAULT_CHARSET));
            documentEventLog.download(dictionary, null);
            return new ResourceGeneration(resourceKey, new ArrayList<>());

        } catch (final Exception e) {
            documentEventLog.download(dictionary, null);
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
