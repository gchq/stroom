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

package stroom.data.store.impl.fs.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.db.util.AbstractFlyWayDbModule;

import java.util.function.Function;

public class FsDataStoreDbModule extends AbstractFlyWayDbModule<DataStoreServiceConfig, FsDataStoreDbConnProvider> {
    private static final String MODULE = "stroom-data-store";
    private static final String FLYWAY_LOCATIONS = "stroom/data/store/impl/fs/db/migration";
    private static final String FLYWAY_TABLE = "fs_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(DataVolumeDao.class).to(DataVolumeDaoImpl.class);
        bind(FsFeedPathDao.class).to(FsFeedPathDaoImpl.class);
        bind(FsTypePathDao.class).to(FsTypePathDaoImpl.class);
        bind(FsVolumeDao.class).to(FsVolumeDaoImpl.class);
        bind(FsVolumeStateDao.class).to(FsVolumeStateDaoImpl.class);
    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Function<HikariConfig, FsDataStoreDbConnProvider> getConnectionProviderConstructor() {
        return FsDataStoreDbConnProvider::new;
    }

    @Override
    protected Class<FsDataStoreDbConnProvider> getConnectionProviderType() {
        return FsDataStoreDbConnProvider.class;
    }
}