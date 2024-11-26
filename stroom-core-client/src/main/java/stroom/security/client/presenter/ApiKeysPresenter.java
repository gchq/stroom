package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.UserRef;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.Supplier;

public class ApiKeysPresenter
        extends ContentTabPresenter<ApiKeysView>
        implements Refreshable, ApiKeyUiHandlers {

    public static final String TAB_TYPE = "ApiKeys";
    private final ApiKeysListPresenter listPresenter;

    @Inject
    public ApiKeysPresenter(final EventBus eventBus,
                            final ApiKeysView view,
                            final ApiKeysListPresenter listPresenter,
                            final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setUiHandlers(this);
        view.setList(listPresenter.getWidget());

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "API Keys Quick Filter",
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

    public void showUser(final UserRef userRef) {
        if (userRef != null) {
            changeQuickFilterInput(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME.getFilterQualifier()
                                   + ":" + userRef.getDisplayName());
        }
    }


    // --------------------------------------------------------------------------------


    public interface ApiKeysView extends View, HasUiHandlers<ApiKeyUiHandlers> {

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void focus();

        void setList(Widget widget);
    }
}
