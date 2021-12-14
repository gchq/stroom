package stroom.node.impl.db;

import stroom.node.impl.NodeDao;

import com.google.inject.AbstractModule;

public class NodeDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(NodeDao.class).to(NodeDaoImpl.class);
    }
}
