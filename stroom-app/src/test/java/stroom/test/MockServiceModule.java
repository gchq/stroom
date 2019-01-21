package stroom.test;

import com.google.inject.AbstractModule;
import org.mockito.stubbing.Answer;
import stroom.security.UserRefFactory;
import stroom.security.UserService;
import stroom.security.shared.UserJooq;
import stroom.security.shared.UserRef;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.activity.impl.mock.MockActivityModule());
        install(new stroom.cache.PipelineCacheModule());
        install(new stroom.data.meta.impl.mock.MockDataMetaModule());
        install(new stroom.data.store.impl.fs.MockStreamStoreModule());
        install(new stroom.dictionary.MockDictionaryModule());
        install(new stroom.docstore.impl.DocStoreModule());
        install(new stroom.docstore.impl.memory.MemoryPersistenceModule());
        install(new stroom.event.logging.impl.EventLoggingModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new stroom.feed.MockFeedModule());
        install(new stroom.importexport.ImportExportModule());
        install(new stroom.index.MockIndexModule());
        install(new stroom.node.MockNodeServiceModule());
        install(new stroom.persist.MockPersistenceModule());
        install(new stroom.pipeline.PipelineModule());
        install(new stroom.pipeline.factory.CommonPipelineElementModule());
        install(new stroom.pipeline.factory.DataStorePipelineElementModule());
        install(new stroom.pipeline.factory.PipelineFactoryModule());
        install(new stroom.pipeline.scope.PipelineScopeModule());
        install(new stroom.pipeline.task.PipelineStreamTaskModule());
        install(new stroom.pipeline.xsltfunctions.CommonXsltFunctionModule());
        install(new stroom.pipeline.xsltfunctions.DataStoreXsltFunctionModule());
        install(new stroom.refdata.ReferenceDataModule());
        install(new stroom.resource.MockResourceModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new stroom.statistics.internal.MockInternalStatisticsModule());
        install(new stroom.streamtask.MockStreamTaskModule());
        install(new stroom.task.MockTaskModule());
        install(new stroom.test.MockTestControlModule());
        install(new stroom.volume.MockVolumeModule());
        install(new stroom.xmlschema.MockXmlSchemaModule());
//        install(new stroom.document.DocumentModule());
//        install(new stroom.entity.MockEntityModule());
//        install(new stroom.properties.impl.mock.MockPropertyModule());
//        install(new stroom.servlet.MockServletModule());

        final UserService mockUserService = mock(UserService.class);
        when(mockUserService.loadByUuid(any())).then((Answer<UserJooq>) invocation -> {
            final String uuid = invocation.getArgument(0);
            final List<UserJooq> list = mockUserService.find(null);
            for (final UserJooq e : list) {
                if (e.getUuid() != null && e.getUuid().equals(uuid)) {
                    return e;
                }
            }
            return null;
        });
        when(mockUserService.createUser(any())).then((Answer<UserRef>) invocation -> {
            final String name = invocation.getArgument(0);
            final UserJooq user = new UserJooq.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .build();
            return UserRefFactory.create(mockUserService.save(user));
        });
        when(mockUserService.createUserGroup(any())).then((Answer<UserRef>) invocation -> {
            final String name = invocation.getArgument(0);
            final UserJooq user = new UserJooq.Builder()
                    .uuid(UUID.randomUUID().toString())
                    .name(name)
                    .isGroup(true)
                    .build();
            return UserRefFactory.create(mockUserService.save(user));
        });
        bind(UserService.class).toInstance(mockUserService);
    }
}
