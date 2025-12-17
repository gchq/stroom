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

package stroom.data.store.impl.fs.db;


import stroom.cache.impl.CacheManagerImpl;
import stroom.data.store.impl.fs.DataStoreServiceConfig.DataStoreServiceDbConfig;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.test.common.util.db.DbTestUtil;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

class TestFsFeedPathDaoImpl extends StroomUnitTest {

    @Test
    void test() {
        final FsDataStoreDbConnProvider fsDataStoreDbConnProvider = DbTestUtil.getTestDbDatasource(
                new FsDataStoreDbModule(), new DataStoreServiceDbConfig());

        final FsFeedPathDaoImpl fsFeedPathDao = new FsFeedPathDaoImpl(
                fsDataStoreDbConnProvider,
                new CacheManagerImpl(),
                FsVolumeConfig::new);

        // Test insert.
        fsFeedPathDao.createPath("test");
        // Test duplicate key.
        fsFeedPathDao.createPath("test");
    }
}
