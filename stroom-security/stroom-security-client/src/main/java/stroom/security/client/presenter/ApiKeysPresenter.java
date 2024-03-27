package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;
import stroom.svg.shared.SvgImage;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class ApiKeysPresenter
        extends ContentTabPresenter<ApiKeysView>
        implements Refreshable, ApiKeyUiHandlers {

    public static final String TAB_TYPE = "ApiKeys";
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

    @Override
    public SvgImage getIcon() {
        return SvgImage.KEY;
    }

    @Override
    public String getLabel() {
        return "Api Keys";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface ApiKeysView extends View, HasUiHandlers<ApiKeyUiHandlers> {

        void focus();

        void setList(Widget widget);
    }
}
