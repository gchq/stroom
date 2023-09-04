package stroom.security.client.presenter;

import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserCriteria;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public abstract class AbstractDataUserListPresenter extends AbstractUserListPresenter {

    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
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
        this.uiConfigCache = uiConfigCache;

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

            if ((filter == null && findUserCriteria.getQuickFilterInput() == null) ||
                    (filter != null && filter.equals(findUserCriteria.getQuickFilterInput()))) {
                return;
            }

            findUserCriteria.setQuickFilterInput(filter);
            dataProvider.refresh();
        }
    }

    public void setup(final FindUserCriteria findUserCriteria) {
        this.findUserCriteria = findUserCriteria;
        dataProvider = new UserDataProvider(getEventBus(), restFactory, getDataGrid());
        dataProvider.setCriteria(findUserCriteria);
        refresh();
    }

    public void refresh() {
        super.refresh();
        if (dataProvider != null) {
            dataProvider.refresh();
        }
    }

    @Override
    public boolean includeAdditionalUserInfo() {
        return findUserCriteria == null || !findUserCriteria.isGroup();
    }
}
