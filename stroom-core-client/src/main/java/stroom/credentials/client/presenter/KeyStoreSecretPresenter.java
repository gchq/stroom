package stroom.credentials.client.presenter;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter.KeyStoreSecretView;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.credentials.shared.Secret;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.form.client.CustomFileUpload;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

public class KeyStoreSecretPresenter
        extends MyPresenterWidget<KeyStoreSecretView> {

    private ResourceKey resourceKey;
    private Consumer<Boolean> afterSubmitConsumer;

    @Inject
    public KeyStoreSecretPresenter(final EventBus eventBus,
                                   final KeyStoreSecretView view) {
        super(eventBus, view);

        view.getFileUpload().setAction(ImportUtil.getImportFileURL());
        view.getFileUpload().setEncoding(FormPanel.ENCODING_MULTIPART);
        view.getFileUpload().setMethod(FormPanel.METHOD_POST);

        final AbstractSubmitCompleteHandler submitCompleteHandler =
                new AbstractSubmitCompleteHandler("Uploading Data", this) {
                    @Override
                    protected void onSuccess(final ResourceKey resourceKey) {
                        KeyStoreSecretPresenter.this.resourceKey = resourceKey;
                        afterSubmitConsumer.accept(true);
                    }

                    @Override
                    protected void onFailure(final String message) {
                        AlertEvent.fireError(KeyStoreSecretPresenter.this, message, () ->
                                afterSubmitConsumer.accept(false));
                    }
                };

        registerHandler(getView().getFileUpload().addSubmitHandler(submitCompleteHandler));
        registerHandler(getView().getFileUpload().addSubmitCompleteHandler(submitCompleteHandler));
    }

    /**
     * Returns the secrets object held by this object.
     *
     * @return A new secrets object updated with any changes.
     */
    public Secret getSecret() {
        return new KeyStoreSecret(getView().getType(), getView().getPassword(), resourceKey);
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            afterSubmitConsumer = null;
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            afterSubmitConsumer = consumer;
            getView().getFileUpload().submit();
        }
    }

    private String validate() {
        if (getView().getType() == null) {
            return "You must choose a key store type, e.g. PKCS12";
        }
        final String fileName = getView().getFileUpload().getFilename();
        if (NullSafe.isBlankString(fileName)) {
            return "You must select a file to import";
        }
        return null;
    }

    public interface KeyStoreSecretView extends View, Focus {

        void setType(final KeyStoreType type);

        KeyStoreType getType();

        String getPassword();

        void setPassword(String password);

        CustomFileUpload getFileUpload();
    }
}
