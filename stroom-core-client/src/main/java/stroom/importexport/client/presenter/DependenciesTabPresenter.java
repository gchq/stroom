package stroom.importexport.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.importexport.client.DependenciesPlugin;
import stroom.importexport.client.event.ShowDocRefDependenciesEvent;
import stroom.importexport.client.presenter.DependenciesTabPresenter.DependenciesTabView;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class DependenciesTabPresenter
        extends ContentTabPresenter<DependenciesTabView>
        implements DependenciesUiHandlers {

    public static final String LIST = "LIST";

    private final DependenciesPresenter dependenciesPresenter;

    @Inject
    public DependenciesTabPresenter(final EventBus eventBus,
                                    final DependenciesTabView view,
                                    final DependenciesPresenter dependenciesPresenter,
                                    final DependenciesPlugin dependenciesPlugin,
                                    final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.dependenciesPresenter = dependenciesPresenter;
        view.setUiHandlers(this);
        setInSlot(LIST, dependenciesPresenter);

        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        view.setHelpTooltipText(QuickFilterTooltipUtil.createTooltip(
                                "Dependencies Quick Filter Syntax",
                                DependencyCriteria.FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter())));

        registerHandler(getEventBus().addHandler(ShowDocRefDependenciesEvent.getType(), event -> {
            dependenciesPlugin.open();
            setQuickFilterText("touuid:" + event.getDocRef().getUuid());
        }));
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.DEPENDENCIES;
    }

    @Override
    public String getLabel() {
        return "Dependencies";
    }

    @Override
    public void changeQuickFilter(final String name) {
        if (name.length() > 0) {
            dependenciesPresenter.setFilterInput(name);
        } else {
            dependenciesPresenter.clearFilterInput();
        }
        dependenciesPresenter.refresh();
    }

    public void setQuickFilterText(final String text) {
        getView().setQuickFilterText(text);
    }

    public interface DependenciesTabView extends View, HasUiHandlers<DependenciesUiHandlers> {

        void setHelpTooltipText(final SafeHtml helpTooltipText);

        void setQuickFilterText(final String text);
    }
}
