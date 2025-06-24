package stroom.contentstore.client.view;

import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter.ContentStoreView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * GWT View implementation for the top-level view of the App Store page.
 */
public class ContentStoreViewImpl extends ViewImpl implements ContentStoreView {

    /** Underlying widget */
    private final Widget widget;

    /** Grid of available content packs, set by setInSlot(). Must be public for GWT. */
    @UiField
    public SimplePanel contentPackList;

    @UiField
    public SimplePanel contentPackDetails;

    /**
     * Injected constructor.
     * @param binder Links this to the XML UI spec.
     */
    @SuppressWarnings("unused")
    @Inject
    public ContentStoreViewImpl(final Binder binder) {
        this.widget = binder.createAndBindUi(this);
    }

    /**
     * Required by GWT.
     * @return the widget.
     */
    @Override
    public Widget asWidget() {
        return widget;
    }

    /**
     * Called to add sub-components to this component.
     * @param slot Where to add the content
     * @param content The content to add in the slot
     */
    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ContentStorePresenter.CONTENT_PACK_LIST.equals(slot)) {
            contentPackList.setWidget(content);
        }
    }

    @Override
    public SimplePanel getContentPackDetailsPanel() {
        return contentPackDetails;
    }

    /**
     * Interface used by ctor; keeps GWT happy.
     */
    public interface Binder extends UiBinder<Widget, ContentStoreViewImpl> {
        // No code
    }

}
