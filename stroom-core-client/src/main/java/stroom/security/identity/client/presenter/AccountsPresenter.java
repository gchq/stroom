package stroom.security.identity.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public class AccountsPresenter
        extends ContentTabPresenter<AccountsView>
        implements Refreshable, AccountsUiHandlers {

    public static final String TAB_TYPE = "Accounts";
    private final AccountsListPresenter listPresenter;

    @Inject
    public AccountsPresenter(final EventBus eventBus,
                             final AccountsView view,
                             final AccountsListPresenter listPresenter,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setUiHandlers(this);
        view.setList(listPresenter.getWidget());

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Accounts Quick Filter",
                        FindApiKeyCriteria.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);
    }

    public void focus() {
        getView().focus();
    }

    @Override
    public void changeQuickFilterInput(final String userInput) {
        listPresenter.setQuickFilter(userInput);
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


    // --------------------------------------------------------------------------------


    public interface AccountsView extends View, HasUiHandlers<AccountsUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void focus();

        void setList(Widget widget);
    }
}
