package stroom.annotation.client;

import stroom.annotation.shared.Annotation;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public class BrowseAnnotationPresenter
        extends MyPresenterWidget<FindView>
        implements TabData, FindUiHandlers, FindDocResultListHandler<Annotation> {

    private final FindAnnotationListPresenter findResultListPresenter;

    @Inject
    public BrowseAnnotationPresenter(final EventBus eventBus,
                                     final FindView view,
                                     final FindAnnotationListPresenter findResultListPresenter) {
        super(eventBus, view);
        this.findResultListPresenter = findResultListPresenter;
        // To browse, users only need view permission.
        findResultListPresenter.setPermission(DocumentPermission.VIEW);
        getView().setDialogMode(false);
        view.setResultView(findResultListPresenter.getView());
        view.setUiHandlers(this);
        findResultListPresenter.setFindResultListHandler(this);
    }

    @Override
    public void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public void refresh() {
        findResultListPresenter.refresh();
    }

    @Override
    public void changeQuickFilter(final String name) {
        findResultListPresenter.setFilter(name);
        findResultListPresenter.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultListPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultListPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.EDIT;
    }

    @Override
    public String getLabel() {
        return "Annotations";
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return "Annotations";
    }
}
