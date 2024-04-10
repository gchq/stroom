package stroom.index.impl.db;

import stroom.index.impl.IndexFieldDao;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;

import com.google.inject.AbstractModule;

public class IndexDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(IndexFieldDao.class).to(IndexFieldDaoImpl.class);
        bind(IndexShardDao.class).to(IndexShardDaoImpl.class);
        bind(IndexVolumeDao.class).to(IndexVolumeDaoImpl.class);
        bind(IndexVolumeGroupDao.class).to(IndexVolumeGroupDaoImpl.class);
    }
}
