package stroom.meta.impl.db;

import stroom.util.shared.Clearable;

import javax.inject.Inject;

public class Cleanup implements Clearable {
    private final MetaValueDaoImpl metaValueDao;
    private final MetaKeyDaoImpl metaKeyDao;
    private final MetaDaoImpl metaDao;
    private final MetaProcessorDaoImpl metaProcessorDao;
    private final MetaTypeDaoImpl metaTypeDao;
    private final MetaFeedDaoImpl metaFeedDao;

    @Inject
    Cleanup(final MetaValueDaoImpl metaValueDao,
            final MetaKeyDaoImpl metaKeyDao,
            final MetaDaoImpl metaDao,
            final MetaProcessorDaoImpl metaProcessorDao,
            final MetaTypeDaoImpl metaTypeDao,
            final MetaFeedDaoImpl metaFeedDao) {
        this.metaValueDao = metaValueDao;
        this.metaKeyDao = metaKeyDao;
        this.metaDao = metaDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaTypeDao = metaTypeDao;
        this.metaFeedDao = metaFeedDao;
    }

    @Override
    public void clear() {
        metaValueDao.clear();
        metaKeyDao.clear();
        metaDao.clear();
        metaProcessorDao.clear();
        metaTypeDao.clear();
        metaFeedDao.clear();
    }
}
