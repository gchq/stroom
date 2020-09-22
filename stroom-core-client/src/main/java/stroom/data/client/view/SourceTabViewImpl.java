package stroom.data.client.view;

import stroom.data.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.data.client.presenter.SourcePresenter;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceTabViewImpl extends ViewImpl implements SourceTabView {

    private final SourcePresenter sourcePresenter;

    private Widget widget;

    @UiField
    ResizeSimplePanel container;

    @Inject
    public SourceTabViewImpl(final EventBus eventBus,
                             final SourcePresenter sourcePresenter,
                             final Binder binder) {
        this.sourcePresenter = sourcePresenter;
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void addToSlot(final Object slot, final Widget content) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setSourceView(final ClassificationWrapperView sourceView) {
        container.setWidget(sourceView.asWidget());
    }

//    @Override
//    public LayerContainer getLayerContainer() {
////        return layerContainer;
//    }

    public interface Binder extends UiBinder<Widget, SourceTabViewImpl> {
    }
}
