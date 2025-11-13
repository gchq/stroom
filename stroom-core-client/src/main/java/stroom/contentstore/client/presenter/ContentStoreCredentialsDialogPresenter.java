package stroom.contentstore.client.presenter;

import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;

/**
 * Dialog to ask users for credentials when they want to install
 * a content pack that needs Git authentication.
 * This content pack is probably a commercial one that requires payment
 * to get the credentials.
 */
public class ContentStoreCredentialsDialogPresenter
        extends MyPresenterWidget<ContentStoreCredentialsDialogPresenter.ContentStoreCredentialsDialogView> {

    /** Converts markdown to HTML */
    private final MarkdownConverter markdownConverter;

    /** Width of dialog */
    private static final int DIALOG_WIDTH = 400;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 400;

    /**
     * Constructor. Injected.
     */
    @Inject
    public ContentStoreCredentialsDialogPresenter(final MarkdownConverter markdownConverter,
                                                  final EventBus eventBus,
                                                  final ContentStoreCredentialsDialogView view) {
        super(eventBus, view);
        this.markdownConverter = markdownConverter;
    }

    /**
     * Call with the builder to set up this dialog before calling .fire().
     * @param contentPack The content pack; provides information to display context
     *           to the user.
     * @param builder The builder to show this popup.
     */
    public void setupDialog(final ContentStoreContentPack contentPack,
                            final ShowPopupEvent.Builder builder) {
        // Get rid of any existing data
        this.getView().resetData();

        // Set the authentication contact information so users know how to
        // get some credentials
        final String markdown = contentPack.getContentStoreMetadata().getAuthContact();
        final SafeHtml html = markdownConverter.convertMarkdownToHtml(markdown);
        this.getView().setAuthContactHtml(html);

        // Configure the popup builder for this dialog
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Credentials")
                .modal(true);
    }

    /**
     * Determines if the data entered is valid.
     */
    public boolean isValid() {
        return !this.getView().getUsername().trim().isEmpty()
                && !this.getView().getPassword().isEmpty();
    }

    /**
     * Returns the message to display in the AlertEvent if something
     * isn't valid.
     */
    public String getValidationMessage() {
        if (this.getView().getUsername().trim().isEmpty()) {
            return "Username must not be empty";
        } else if (this.getView().getPassword().isEmpty()) {
            return "Password must not be empty";
        } else {
            return "";
        }
    }

    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface ContentStoreCredentialsDialogView extends View {

        /**
         * Sets the HTML that tells users how to get authentication credentials.
         * @param authContactHtml The HTML from Markdown giving the contact info.
         */
        void setAuthContactHtml(SafeHtml authContactHtml);

        /**
         * @return The username entered by the user.
         */
        String getUsername();

        /**
         * @return The password entered by the user.
         */
        String getPassword();

        /**
         * Resets all dialog information for the next use.
         */
        void resetData();

    }
}
