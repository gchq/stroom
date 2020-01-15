package stroom.meta.impl;

import stroom.meta.api.PhysicalDelete;

import javax.inject.Inject;
import java.util.List;

class PhysicalDeleteImpl implements PhysicalDelete {
    private final MetaDao metaDao;
    private final MetaValueDao metaValueDao;

    @Inject
    PhysicalDeleteImpl(final MetaDao metaDao, final MetaValueDao metaValueDao) {
        this.metaDao = metaDao;
        this.metaValueDao = metaValueDao;
    }

    @Override
    public void cleanup(final List<Long> idList) {
        // Delete meta attributes.
        metaValueDao.delete(idList);

        // Delete meta data.
        metaDao.delete(idList);
    }
}
