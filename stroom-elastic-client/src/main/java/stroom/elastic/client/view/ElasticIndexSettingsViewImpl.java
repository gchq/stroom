package stroom.elastic.client.view;

import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.elastic.client.presenter.ElasticIndexSettingsPresenter;

public class ElasticIndexSettingsViewImpl extends ViewImpl implements ElasticIndexSettingsPresenter.ElasticIndexSettingsView {
    private final Widget widget;

    @UiField
    TextBox indexName;

    @UiField
    TextBox indexedType;

    @Inject
    public ElasticIndexSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return indexName.addKeyDownHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        widget.fireEvent(event);
    }

    @Override
    public String getIndexName() {
        return indexName.getText();
    }

    @Override
    public void setIndexName(String value) {
        indexName.setText(value);
    }

    @Override
    public String getIndexedType() {
        return indexedType.getText();
    }

    @Override
    public void setIndexedType(String value) {
        indexedType.setText(value);
    }

    public interface Binder extends UiBinder<Widget, ElasticIndexSettingsViewImpl> {
    }

}
