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
