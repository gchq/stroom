package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.DataVolumeDao;
import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.data.store.impl.fs.FsOrphanedMetaDao;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeCache;
import stroom.data.store.impl.fs.FsVolumeDao;
import stroom.data.store.impl.fs.FsVolumeGroupDao;
import stroom.data.store.impl.fs.FsVolumeStateDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class FsDataStoreDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(DataVolumeDao.class).to(DataVolumeDaoImpl.class);
        bind(FsFeedPathDao.class).to(FsFeedPathDaoImpl.class);
        bind(FsTypePathDao.class).to(FsTypePathDaoImpl.class);
        bind(FsVolumeDao.class).to(FsVolumeDaoImpl.class);
        bind(FsVolumeGroupDao.class).to(FsVolumeGroupDaoImpl.class);
        bind(FsVolumeStateDao.class).to(FsVolumeStateDaoImpl.class);
        bind(FsOrphanedMetaDao.class).to(FsOrphanedMetaDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(FsVolumeCache.class);
    }
}
