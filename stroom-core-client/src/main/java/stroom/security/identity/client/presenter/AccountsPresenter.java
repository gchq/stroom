package stroom.security.identity.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class AccountsPresenter
        extends ContentTabPresenter<AccountsView>
        implements Refreshable {

    public static final String TAB_TYPE = "Accounts";
    private final AccountsListPresenter listPresenter;

    @Inject
    public AccountsPresenter(final EventBus eventBus,
                             final AccountsView view,
                             final AccountsListPresenter listPresenter,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setList(listPresenter.getWidget());
    }

    public void focus() {
        getView().focus();
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.USER;
    }

    @Override
    public String getLabel() {
        return "Manage Accounts";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public void setFilterInput(final String filterInput) {
        listPresenter.setQuickFilterText(filterInput);
    }


    // --------------------------------------------------------------------------------


    public interface AccountsView extends View {

        void focus();

        void setList(Widget widget);
    }
}
