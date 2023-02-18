package stroom.importexport.client.presenter;

import stroom.docref.DocRef;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import javax.inject.Inject;

public class DependenciesInfoPresenter extends MyPresenter<DependenciesInfoPresenter.DependenciesInfoView,
        DependenciesInfoPresenter.DependenciesInfoProxy> implements ShowDependenciesInfoDialogEvent.Handler {

    @Inject
    public DependenciesInfoPresenter(final EventBus eventBus,
                                     final DependenciesInfoView view,
                                     final DependenciesInfoProxy proxy) {
        super(eventBus, view, proxy);
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(400, 200);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Dependency Information")
                .fire();
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowDependenciesInfoDialogEvent event) {
        final DocRef docRef = event.getDocRef();
        final StringBuilder sb = new StringBuilder();

        sb.append("Type: ");
        sb.append(docRef.getType());
        sb.append("\nUUID: ");
        sb.append(docRef.getUuid());
        sb.append("\nName: ");
        sb.append(docRef.getName());

        getView().setInfo(sb.toString());
        forceReveal();
    }

    @ProxyCodeSplit
    public interface DependenciesInfoProxy extends Proxy<DependenciesInfoPresenter> {

    }

    public interface DependenciesInfoView extends View {
        void setInfo(String info);
    }
}
