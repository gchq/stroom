package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationDao;
import stroom.annotation.impl.AnnotationTagDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class AnnotationDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnnotationDao.class).to(AnnotationDaoImpl.class);
        bind(AnnotationTagDao.class).to(AnnotationTagDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(AnnotationDaoImpl.class)
                .addBinding(AnnotationTagDaoImpl.class)
                .addBinding(AnnotationFeedCache.class);
    }
}
