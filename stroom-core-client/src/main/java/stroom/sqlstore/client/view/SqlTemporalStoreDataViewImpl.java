package stroom.sqlstore.client.view;

import stroom.query.client.view.QueryDataViewImpl;
import stroom.sqlstore.client.presenter.SqlTemporalStoreDataPresenter.SqlTemporalStoreDataView;

import com.google.inject.Inject;

public class SqlTemporalStoreDataViewImpl extends QueryDataViewImpl implements SqlTemporalStoreDataView {

    @Inject
    public SqlTemporalStoreDataViewImpl(final Binder binder) {
        super(binder);
    }
}
