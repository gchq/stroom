package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.cache.service.impl.CacheManagerService;
import stroom.cache.service.impl.CacheManagerServiceImpl;
import stroom.cache.service.impl.FindCacheInfoCriteria;
import stroom.docstore.shared.AbstractDoc;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.StringCriteria;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class TestAbstractDocPool {

    protected static final String CACHE_NAME = "MyDocPoolCache";
    @Mock
    DocumentPermissionCache documentPermissionCache;

    final CacheManagerImpl cacheManager = new CacheManagerImpl();

    final CacheManagerService cacheManagerService = new CacheManagerServiceImpl(
            cacheManager,
            new MockSecurityContext(),
            new SimpleTaskContextFactory());

    final AtomicInteger valueCreationCounter = new AtomicInteger();

    final CacheConfig cacheConfig = CacheConfig.builder()
            .build();

    @BeforeEach
    void setUp() {
        cacheConfig.setBasePath(PropertyPath.fromParts("test", CACHE_NAME));
        Mockito.when(documentPermissionCache.canUseDocument(Mockito.any()))
                .thenReturn(true);
    }

    @Test
    void testBorrowReturn() {
        final MyDocPool myDocPool = new MyDocPool(
                cacheManager,
                () -> cacheConfig,
                documentPermissionCache,
                this::createPoolValueWithCounter);

        final MyDoc myDoc = MyDoc.builder().uuid("foo").build();
        final PoolItem<String> poolItem1 = myDocPool.borrowObject(myDoc, true);

        Assertions.assertThat(valueCreationCounter)
                .hasValue(1);

        Assertions.assertThat(poolItem1.getValue())
                .isEqualTo(createPoolValue(myDoc));
        Assertions.assertThat(poolItem1.getKey().getKey())
                .isEqualTo(myDoc);

        myDocPool.returnObject(poolItem1, true);

        PoolItem<String> poolItem2 = myDocPool.borrowObject(myDoc, true);

        // Using value created above that is back in the pool
        Assertions.assertThat(valueCreationCounter)
                .hasValue(1);

        // borrow another while poolItem2 is still out on loan
        PoolItem<String> poolItem3 = myDocPool.borrowObject(myDoc, true);

        Assertions.assertThat(valueCreationCounter)
                .hasValue(2);

        myDocPool.returnObject(poolItem2, true);
        myDocPool.returnObject(poolItem3, true);

        // Now reborrow 2+3 again which should come from pool
        poolItem2 = myDocPool.borrowObject(myDoc, true);
        poolItem3 = myDocPool.borrowObject(myDoc, true);

        Assertions.assertThat(valueCreationCounter)
                .hasValue(2);
    }

    @Test
    void testBorrowReturn_notPooled() {
        Assertions.assertThat(valueCreationCounter)
                .hasValue(0);

        final MyDocPool myDocPool = new MyDocPool(
                cacheManager,
                () -> cacheConfig,
                documentPermissionCache,
                this::createPoolValueWithCounter);


        final MyDoc myDoc = MyDoc.builder().uuid("foo").build();
        final PoolItem<String> poolItem1 = myDocPool.borrowObject(myDoc, false);

        Assertions.assertThat(valueCreationCounter)
                .hasValue(1);
        Assertions.assertThat(poolItem1.getValue())
                .isEqualTo(createPoolValue(myDoc));
        Assertions.assertThat(poolItem1.getKey().getKey())
                .isEqualTo(myDoc);

        myDocPool.returnObject(poolItem1, false);

        final PoolItem<String> poolItem2 = myDocPool.borrowObject(myDoc, false);

        // Item 1 didn't go in the pool so value created fresh
        Assertions.assertThat(valueCreationCounter)
                .hasValue(2);

        myDocPool.returnObject(poolItem2, false);
    }

    @Test
    void testBorrowRebuildCacheThenReturn() {
        final MyDocPool myDocPool = new MyDocPool(
                cacheManager,
                () -> cacheConfig,
                documentPermissionCache,
                this::createPoolValueWithCounter);

        final MyDoc myDoc = MyDoc.builder().uuid("foo").build();
        final PoolItem<String> poolItem1 = myDocPool.borrowObject(myDoc, true);

        Assertions.assertThat(poolItem1.getValue())
                .isEqualTo(createPoolValue(myDoc));
        Assertions.assertThat(poolItem1.getKey().getKey())
                .isEqualTo(myDoc);

        final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
        criteria.setName(new StringCriteria(CACHE_NAME, null));
        cacheManagerService.clear(new FindCacheInfoCriteria());

        // Return to the pool that has a new empty cache so the item won't be held
        // by the pool
        myDocPool.returnObject(poolItem1, true);

        final PoolItem<String> poolItem2 = myDocPool.borrowObject(myDoc, true);

        // Latest borrow causes value to be created as there is nothing in the pool
        Assertions.assertThat(valueCreationCounter)
                .hasValue(2);

        myDocPool.returnObject(poolItem2, true);
    }

    private String createPoolValueWithCounter(final MyDoc myDoc) {
        valueCreationCounter.incrementAndGet();
        return createPoolValue(myDoc);
    }

    private String createPoolValue(final MyDoc myDoc) {
        return myDoc.getContent().toUpperCase();
    }

    private static class MyDocPool extends AbstractDocPool<MyDoc, String> {

        private final Function<MyDoc, String> valueCreator;

        public MyDocPool(final CacheManager cacheManager,
                         final Supplier<CacheConfig> cacheConfigProvider,
                         final DocumentPermissionCache documentPermissionCache,
                         final Function<MyDoc, String> valueCreator) {
            super(
                    cacheManager,
                    CACHE_NAME,
                    cacheConfigProvider,
                    documentPermissionCache,
                    new MockSecurityContext());
            this.valueCreator = valueCreator;
        }

        @Override
        protected String createValue(final MyDoc myDoc) {
            return valueCreator.apply(myDoc);
        }
    }

    private static class MyDoc extends AbstractDoc {

        private final String content;

        public MyDoc(@JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser,
                     final String content) {
            super("MyDoc", uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder extends AbstractDoc.AbstractBuilder<MyDoc, MyDoc.Builder> {

            private String content = "";

            private Builder() {
            }

            private Builder(final MyDoc doc) {
                super(doc);
                this.content = doc.content;
            }

            public Builder content(final String content) {
                this.content = content;
                return self();
            }

            @Override
            protected Builder self() {
                return this;
            }

            public MyDoc build() {
                return new MyDoc(
                        uuid,
                        name,
                        version,
                        createTimeMs,
                        updateTimeMs,
                        createUser,
                        updateUser,
                        content);
            }
        }
    }
}
