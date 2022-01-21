package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationDao;

import com.google.inject.AbstractModule;

public class AnnotationDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnnotationDao.class).to(AnnotationDaoImpl.class);
    }
}
