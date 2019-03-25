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

package stroom.data.store.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.db.util.DbUtil;
import stroom.meta.impl.db.ConnectionProvider;
import stroom.meta.shared.MetaProperties;
import stroom.data.shared.StreamTypeNames;
import stroom.test.common.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;

class TestStreamGrepTool {
    @Inject
    private ConnectionProvider connectionProvider;
    @Inject
    private Store streamStore;

    @BeforeEach
    void setup() {
        new ToolInjector().getInjector().injectMembers(this);

        try (final Connection connection = connectionProvider.getConnection()) {
            DbUtil.clearAllTables(connection);
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    void test() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        try {
            addData(feedName, "This is some test data to match on");
            addData(feedName, "This is some test data to not match on");

            final StreamGrepTool streamGrepTool = new StreamGrepTool();
            streamGrepTool.setFeed(feedName);

            streamGrepTool.run();

        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void addData(final String feedName, final String data) {
        final MetaProperties metaProperties = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
            TargetUtil.write(streamTarget, data);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
