/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import org.mockito.Mockito;

public class DirQueueFactoryFactory {

    public static DirQueueFactory create(final DataDirProvider dataDirProvider) {
        final QueueMonitors mockQueueMonitors = Mockito.mock(QueueMonitors.class);
        final QueueMonitor mockQueueMonitor = Mockito.mock(QueueMonitor.class);
        Mockito.when(mockQueueMonitors.create(Mockito.any(), Mockito.any()))
                .thenReturn(mockQueueMonitor);
        final FileStores mockFileStores = Mockito.mock(FileStores.class);
        return new DirQueueFactory(
                dataDirProvider,
                mockQueueMonitors,
                mockFileStores);
    }
}
