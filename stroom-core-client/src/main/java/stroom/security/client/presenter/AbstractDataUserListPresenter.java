package stroom.security.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.ui.config.client.UiConfigCache;

public abstract class AbstractDataUserListPresenter extends AbstractUserListPresenter {

    private final RestFactory restFactory;
    private UserDataProvider dataProvider;
    private FindUserCriteria findUserCriteria;

    @Inject
    public AbstractDataUserListPresenter(final EventBus eventBus,
                                         final UserListView userListView,
                                         final PagerView pagerView,
                                         final RestFactory restFactory,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus, userListView, pagerView, uiConfigCache);
        this.restFactory = restFactory;

    }

    public FindUserCriteria getFindUserCriteria() {
        return findUserCriteria;
    }

    @Override
    public void changeNameFilter(String name) {
        if (findUserCriteria != null) {
            String filter = name;

            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            try {
                final ExpressionOperator expression = QuickFilterExpressionParser
                        .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
                findUserCriteria.setExpression(expression);
                dataProvider.refresh();

            } catch (final RuntimeException e) {
                GWT.log(e.getMessage());
            }
        }
    }

    public void setup(final FindUserCriteria findUserCriteria) {
        this.findUserCriteria = findUserCriteria;
        dataProvider = new UserDataProvider(getEventBus(), restFactory, getDataGrid());
        dataProvider.setCriteria(findUserCriteria, pagerView);
        refresh();
    }

    public void refresh() {
        super.refresh();
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }
}
