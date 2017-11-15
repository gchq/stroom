package stroom.elastic.client.presenter;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ElasticIndexSettingsPresenter extends MyPresenterWidget<ElasticIndexSettingsPresenter.ElasticIndexSettingsView>
        implements HasKeyDownHandlers {

    @Inject
    public ElasticIndexSettingsPresenter(final EventBus eventBus,
                                         final ElasticIndexSettingsPresenter.ElasticIndexSettingsView view) {
        super(eventBus, view);
    }

    public String getIndexName() {
        return getView().getIndexName();
    }

    public void setIndexName(final String text) {
        getView().setIndexName(text);
    }

    public String getIndexedType() {
        return getView().getIndexedType();
    }

    public void setIndexedType(final String text) {
        getView().setIndexedType(text);
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return getView().addKeyDownHandler(handler);
    }

    public interface ElasticIndexSettingsView extends View, HasKeyDownHandlers {
        String getIndexName();
        void setIndexName(String value);

        String getIndexedType();
        void setIndexedType(String value);
    }
}
