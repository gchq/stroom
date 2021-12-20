package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeStateDao;

import com.google.inject.AbstractModule;

public class FsDataStoreDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(DataVolumeDao.class).to(DataVolumeDaoImpl.class);
        bind(FsFeedPathDao.class).to(FsFeedPathDaoImpl.class);
        bind(FsTypePathDao.class).to(FsTypePathDaoImpl.class);
        bind(FsVolumeDao.class).to(FsVolumeDaoImpl.class);
        bind(FsVolumeStateDao.class).to(FsVolumeStateDaoImpl.class);
    }
}
