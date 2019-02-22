package stroom.test;

import com.google.inject.AbstractModule;
import org.mockito.stubbing.Answer;
import stroom.cache.impl.CacheModule;
import stroom.data.store.impl.mock.MockStreamStoreModule;
import stroom.explorer.impl.MockExplorerModule;
import stroom.feed.impl.MockFeedModule;
import stroom.meta.impl.mock.MockMetaModule;
import stroom.dataprocess.PipelineStreamTaskModule;
import stroom.dictionary.impl.MockDictionaryModule;
import stroom.importexport.impl.ImportExportModule;
import stroom.node.impl.mock.MockNodeServiceModule;
import stroom.pipeline.xmlschema.MockXmlSchemaModule;
import stroom.resource.impl.MockResourceModule;
import stroom.security.impl.UserRefFactory;
import stroom.security.impl.UserService;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.task.impl.MockTaskModule;
import stroom.util.pipeline.scope.PipelineScopeModule;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.activity.impl.mock.MockActivityModule());
        install(new CacheModule());
        install(new MockMetaModule());
        install(new MockStreamStoreModule());
        install(new MockDictionaryModule());
        install(new stroom.docstore.impl.DocStoreModule());
        install(new stroom.docstore.impl.memory.MemoryPersistenceModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new MockExplorerModule());
        install(new MockFeedModule());
        install(new ImportExportModule());
        install(new stroom.index.MockIndexModule());
        install(new MockNodeServiceModule());
        install(new stroom.persist.MockPersistenceModule());
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
        install(new MockResourceModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new stroom.statistics.impl.mock.MockInternalStatisticsModule());
        install(new stroom.streamtask.MockStreamTaskModule());
        install(new MockTaskModule());
        install(new stroom.test.MockTestControlModule());
        install(new stroom.volume.MockVolumeModule());
        install(new MockXmlSchemaModule());
//        install(new stroom.document.DocumentModule());
//        install(new stroom.entity.MockEntityModule());
//        install(new stroom.properties.impl.mock.MockPropertyModule());
//        install(new stroom.servlet.MockServletModule());

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
        when(mockUserService.createUser(any())).then((Answer<UserRef>) invocation -> {
            final String name = invocation.getArgument(0);
            final User user = new User.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .build();
            return UserRefFactory.create(mockUserService.save(user));
        });
        when(mockUserService.createUserGroup(any())).then((Answer<UserRef>) invocation -> {
            final String name = invocation.getArgument(0);
            final User user = new User.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .isGroup(true)
                    .build();
            return UserRefFactory.create(mockUserService.save(user));
        });
        bind(UserService.class).toInstance(mockUserService);
    }
}
