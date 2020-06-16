package stroom.importexport.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.importexport.client.presenter.DependenciesTabPresenter.DependenciesTabView;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;

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
    public void changeNameFilter(final String name) {
        if (name.length() > 0) {
            dependenciesPresenter.setFilterInput(name);
        } else {
            dependenciesPresenter.clearFilterInput();
        }
        dependenciesPresenter.refresh();
    }

    public interface DependenciesTabView extends View, HasUiHandlers<DependenciesUiHandlers> {

    }
}
