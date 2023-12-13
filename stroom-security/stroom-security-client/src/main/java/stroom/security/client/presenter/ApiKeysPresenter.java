package stroom.security.client.presenter;

import stroom.data.table.client.Refreshable;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ApiKeysPresenter
        extends MyPresenterWidget<ApiKeysView>
        implements Refreshable, ApiKeyUiHandlers {

    private final ApiKeysListPresenter listPresenter;

    @Inject
    public ApiKeysPresenter(final EventBus eventBus,
                            final ApiKeysView view,
                            final ApiKeysListPresenter listPresenter) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setUiHandlers(this);
        view.setList(listPresenter.getWidget());
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


    // --------------------------------------------------------------------------------


    public interface ApiKeysView extends View, HasUiHandlers<ApiKeyUiHandlers> {

        void focus();

        void setList(Widget widget);
    }
}
