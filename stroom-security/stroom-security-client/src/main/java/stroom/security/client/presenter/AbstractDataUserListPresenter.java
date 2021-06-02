package stroom.security.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.security.shared.FindUserCriteria;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public abstract class AbstractDataUserListPresenter extends AbstractUserListPresenter {

    private final RestFactory restFactory;
    private UserDataProvider dataProvider;
    private FindUserCriteria findUserCriteria;

    @Inject
    public AbstractDataUserListPresenter(final EventBus eventBus,
                                         final UserListView userListView,
                                         final RestFactory restFactory) {
        super(eventBus, userListView);
        this.restFactory = restFactory;
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                if (findUserCriteria != null && findUserCriteria.getRelatedUser() == null) {
                    hide();
                }
            }
        }));
    }

    void hide() {
        HidePopupEvent.fire(
                this,
                this,
                false,
                true);
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
        dataProvider = new UserDataProvider(getEventBus(), restFactory, getDataGridView());
        dataProvider.setCriteria(findUserCriteria);
        refresh();
    }

    public void refresh() {
        dataProvider.refresh();
    }
}
