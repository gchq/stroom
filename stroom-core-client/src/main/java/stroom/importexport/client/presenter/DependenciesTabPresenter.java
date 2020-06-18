package stroom.importexport.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.importexport.client.presenter.DependenciesTabPresenter.DependenciesTabView;
import stroom.importexport.shared.DependencyCriteria;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

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
                                    final DependenciesPresenter dependenciesPresenter) {
        super(eventBus, view);
        this.dependenciesPresenter = dependenciesPresenter;
        view.setUiHandlers(this);
        setInSlot(LIST, dependenciesPresenter);

        view.setHelpTooltipText(QuickFilterTooltipUtil.createTooltip(
                "Dependencies Quick Filter Syntax",
                DependencyCriteria.FIELD_DEFINITIONS));
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

    public interface DependenciesTabView extends View, HasUiHandlers<DependenciesUiHandlers> {

        void setHelpTooltipText(final String helpTooltipText);
    }
}
