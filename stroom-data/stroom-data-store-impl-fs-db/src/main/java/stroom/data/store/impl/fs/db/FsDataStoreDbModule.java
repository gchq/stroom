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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.db.util.AbstractFlyWayDbModule;

import java.util.function.Function;

public class FsDataStoreDbModule extends AbstractFlyWayDbModule<DataStoreServiceConfig, ConnectionProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsDataStoreDbModule.class);
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
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, ConnectionProvider> getConnectionProviderConstructor() {
        return ConnectionProvider::new;
    }

    @Override
    public Class<ConnectionProvider> getConnectionProviderType() {
        return ConnectionProvider.class;
    }
}