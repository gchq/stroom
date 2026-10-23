package stroom.document.asset.client.view;

import stroom.document.asset.client.presenter.DocumentAssetChooserPresenter.DocumentAssetChooserView;
import stroom.document.asset.client.presenter.assets.DocumentAssetTree;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DocumentAssetChooserViewImpl extends ViewImpl implements DocumentAssetChooserView {

    private final Widget widget;

    @UiField
    SimplePanel treePanel;

    @Inject
    public DocumentAssetChooserViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTree(final DocumentAssetTree tree) {
        treePanel.setWidget(tree);
    }

    public interface Binder extends UiBinder<Widget, DocumentAssetChooserViewImpl> {
    }
}
