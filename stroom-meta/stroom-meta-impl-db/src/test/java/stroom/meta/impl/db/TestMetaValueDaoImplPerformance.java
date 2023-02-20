/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.datasource.api.v2.AbstractField;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.event.logging.mock.MockStroomEventLoggingModule;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaModule;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.MetaServiceImpl;
import stroom.meta.impl.MetaValueConfig;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.inject.Inject;

class TestMetaValueDaoImplPerformance {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaValueDaoImplPerformance.class);

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaServiceImpl metaService;
    @Inject
    private MetaValueDaoImpl metaValueDao;

    private MetaServiceConfig metaServiceConfig = new MetaServiceConfig();

    @BeforeEach
    void setup() {
        Guice
                .createInjector(
                        new MetaModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new CacheModule(),
                        new DbTestModule(),
                        new MetaTestModule(),
                        new MockTaskModule(),
                        new MockStroomEventLoggingModule(),
                        new AbstractModule() {

                            @Override
                            protected void configure() {
                                bind(MetaServiceConfig.class).toProvider(() ->
                                        getMetaServiceConfig());
                                bind(MetaValueConfig.class).toProvider(() ->
                                        getMetaValueConfig());
                            }
                        })
                .injectMembers(this);
        setAddAsync(true);
        // Delete everything
        cleanup.cleanup();
    }

    @AfterEach
    void unsetProperties() {
        setAddAsync(true);
    }

    @Test
    void testAddPerformance() {
        final int metaCount = 1_000;
        final int metaValCount = MetaFields.getExtendedFields().size() * metaCount;
        final boolean isParallel = true;

        final List<Meta> metaList = LongStream.rangeClosed(1, metaCount)
                .boxed()
                .map(i ->
                        metaService.create(createProperties("FEED1")))
                .collect(Collectors.toList());
        final AttributeMap allAttributes = createAllAttributes();

        final DurationTimer timer = DurationTimer.start();

        var stream = metaList.stream();
        if (isParallel) {
            stream.parallel();
        }

        final List<Duration> durations = stream
                .map(meta -> {
                    DurationTimer addTimer = DurationTimer.start();
                    metaValueDao.addAttributes(meta, allAttributes);
                    return addTimer.get();
                })
                .collect(Collectors.toList());

//        for (final Meta meta : metaList) {
//            metaValueDao.addAttributes(meta, createAllAttributes());
//        }

        metaValueDao.shutdown();

        LOGGER.info("Loaded {} meta, {} metaVal in {}", metaCount, metaValCount, timer);

        LOGGER.info("stats: {}",
                durations.stream()
                        .mapToLong(Duration::toMillis)
                        .summaryStatistics());
    }

    private MetaProperties createProperties(final String feedName) {
        return MetaProperties.builder()
                .createMs(1000L)
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName("TEST_STREAM_TYPE")
                .build();
    }

    private AttributeMap createAllAttributes() {
        final AttributeMap attributeMap = new AttributeMap();
        for (final AbstractField field : MetaFields.getExtendedFields()) {
            attributeMap.put(field.getName(), "100");
        }
        return attributeMap;
    }

    public MetaServiceConfig getMetaServiceConfig() {
        return metaServiceConfig;
    }

    public MetaValueConfig getMetaValueConfig() {
        return metaServiceConfig.getMetaValueConfig();
    }

    private void setAddAsync(final boolean addAsync) {
        metaServiceConfig = metaServiceConfig.withMetaValueConfig(
                metaServiceConfig.getMetaValueConfig()
                        .withAddAsync(addAsync));
    }
}
