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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestOrphanMetaFinder extends AbstractCoreIntegrationTest {

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private FsOrphanMetaFinder fsOrphanMetaFinder;
    @Inject
    private FsFileFinder fileFinder;

    @Test
    void test() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta meta = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        final SimpleTaskContext taskContext = new SimpleTaskContext();
        final AtomicLong count = new AtomicLong();
        final Consumer<SimpleMeta> orphanConsumer = m -> count.incrementAndGet();
        fsOrphanMetaFinder.scan(orphanConsumer, taskContext);
        assertThat(count.get()).isZero();

        final List<Path> paths = fileFinder.findAllStreamFile(meta);
        paths.forEach(FileUtil::deleteFile);
        fsOrphanMetaFinder.scan(orphanConsumer, taskContext);
        assertThat(count.get()).isOne();
    }
}
