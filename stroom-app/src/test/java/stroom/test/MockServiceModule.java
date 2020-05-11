package stroom.test;

import stroom.activity.mock.MockActivityModule;
import stroom.cache.impl.CacheModule;
import stroom.core.dataprocess.PipelineStreamTaskModule;
import stroom.data.store.mock.MockStreamStoreModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.explorer.impl.MockExplorerModule;
import stroom.feed.impl.MockFeedModule;
import stroom.importexport.impl.ImportExportModule;
import stroom.index.mock.MockIndexModule;
import stroom.meta.mock.MockMetaModule;
import stroom.node.mock.MockNodeServiceModule;
import stroom.pipeline.xmlschema.MockXmlSchemaModule;
import stroom.processor.impl.MockProcessorModule;
import stroom.resource.impl.MockResourceModule;
import stroom.security.impl.UserService;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.shared.User;
import stroom.statistics.mock.MockInternalStatisticsModule;
import stroom.task.impl.MockTaskModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.pipeline.scope.PipelineScopeModule;
import stroom.util.servlet.MockServletModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new MockActivityModule());
        install(new CacheModule());
        install(new MockMetaModule());
        install(new MockStreamStoreModule());
        install(new MockWordListProviderModule());
        install(new stroom.docstore.impl.DocStoreModule());
        install(new stroom.docstore.impl.memory.MemoryPersistenceModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new MockExplorerModule());
        install(new MockFeedModule());
        install(new ImportExportModule());
        install(new MockIndexModule());
        install(new MockNodeServiceModule());
        install(new stroom.pipeline.PipelineModule());
        install(new stroom.pipeline.cache.PipelineCacheModule());
        install(new stroom.pipeline.factory.CommonPipelineElementModule());
        install(new stroom.pipeline.factory.DataStorePipelineElementModule());
        install(new stroom.pipeline.factory.PipelineFactoryModule());
        install(new PipelineScopeModule());
        install(new PipelineStreamTaskModule());
        install(new stroom.pipeline.xsltfunctions.CommonXsltFunctionModule());
        install(new stroom.pipeline.xsltfunctions.DataStoreXsltFunctionModule());
        install(new stroom.pipeline.refdata.ReferenceDataModule());
        install(new stroom.processor.mock.MockProcessorModule());
        install(new MockResourceModule());
        install(new MockSecurityContextModule());
        install(new stroom.task.impl.MockTaskModule());
        install(new MockInternalStatisticsModule());
        install(new MockProcessorModule());
        install(new MockTaskModule());
        install(new stroom.test.MockTestControlModule());
        install(new MockServletModule());
        install(new MockXmlSchemaModule());

        final UserService mockUserService = mock(UserService.class);
        when(mockUserService.loadByUuid(any())).then((Answer<User>) invocation -> {
            final String uuid = invocation.getArgument(0);
            final List<User> list = mockUserService.find(null);
            for (final User e : list) {
                if (e.getUuid() != null && e.getUuid().equals(uuid)) {
                    return e;
                }
            }
            return null;
        });
        when(mockUserService.createUser(any())).then((Answer<User>) invocation -> {
            final String name = invocation.getArgument(0);
            final User user = new User.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .build();
            return mockUserService.update(user);
        });
        when(mockUserService.createUserGroup(any())).then((Answer<User>) invocation -> {
            final String name = invocation.getArgument(0);
            final User user = new User.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .group(true)
                    .build();
            return mockUserService.update(user);
        });
        bind(UserService.class).toInstance(mockUserService);
    }

    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }
}
