package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

import javax.inject.Provider;

public class DocumentEditTabProvider<D>
        extends HandlerContainerImpl
        implements TabProvider<D> {

    private final Provider<DocumentEditPresenter<?, D>> presenterWidgetProvider;

    private DocumentEditPresenter<?, D> widget;

    public DocumentEditTabProvider(final Provider<DocumentEditPresenter<?, D>> presenterWidgetProvider) {
        this.presenterWidgetProvider = presenterWidgetProvider;
    }

    @Override
    public DocumentEditPresenter<?, D> getPresenter() {
        if (widget == null) {
            widget = presenterWidgetProvider.get();
        }
        return widget;
    }

    @Override
    public void read(final DocRef docRef, final D document, final boolean readOnly) {
        getPresenter().read(docRef, document, readOnly);
    }

    @Override
    public D write(final D document) {
        return getPresenter().write(document);
    }

    @Override
    public void onClose() {
        getPresenter().onClose();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return getPresenter().addDirtyHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        getPresenter().fireEvent(event);
    }
}
