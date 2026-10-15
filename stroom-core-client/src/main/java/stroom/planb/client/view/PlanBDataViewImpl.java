package stroom.planb.client.view;

import stroom.planb.client.presenter.PlanBDataPresenter.PlanBDataView;
import stroom.query.client.view.QueryDataViewImpl;

import com.google.inject.Inject;

public class PlanBDataViewImpl extends QueryDataViewImpl implements PlanBDataView {

    @Inject
    public PlanBDataViewImpl(final Binder binder) {
        super(binder);
    }
}
