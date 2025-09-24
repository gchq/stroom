package stroom.credentials.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsResource;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MultiSelectEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Represents the Credentials Tab of the UI.
 * Ties together the List and Details view of the data.
 */
public class CredentialsPresenter
        extends ContentTabPresenter<CredentialsPresenter.CredentialsView> {

    /** Reference to the List view within this tab */
    private final CredentialsListPresenter credentialsListPresenter;

    /** Reference to the details view within this tab */
    private final CredentialsDetailsPresenter credentialsDetailsPresenter;

    /** Label for the content */
    private static final String LABEL = "Credentials";

    /** Tab type for the content (what is this?) */
    private static final String TAB_TYPE = "Credentials";

    /** ID of the presenter for the list of credentials */
    public static final String CREDENTIALS_LIST = "CREDENTIALS_LIST";

    /** The resource to access server-side data */
    public static final CredentialsResource CREDENTIALS_RESOURCE
            = GWT.create(CredentialsResource.class);

    /**
     * Injected constructor.
     * @param eventBus Passed to superclass
     * @param view Passed to superclass
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsPresenter(final EventBus eventBus,
                                final CredentialsView view,
                                final CredentialsListPresenter credentialsListPresenter,
                                final CredentialsDetailsPresenter credentialsDetailsPresenter) {
        super(eventBus, view);
        this.credentialsListPresenter = credentialsListPresenter;
        this.credentialsListPresenter.setCredentialsPresenter(this);
        this.credentialsDetailsPresenter = credentialsDetailsPresenter;
        this.credentialsDetailsPresenter.setCredentialsPresenter(this);
        this.setInSlot(CREDENTIALS_LIST, credentialsListPresenter);
        view.getDetailsPanel().add(credentialsDetailsPresenter);
    }

    /**
     * GWT callback.
     */
    @Override
    protected void onBind() {
        super.onBind();

        // Add in the handler on the list selection
        credentialsListPresenter.getSelectionModel().addSelectionHandler(this::credentialsListSelectionHandler);
    }

    /**
     * @return the icon to display for the content.
     */
    @Override
    public SvgImage getIcon() {
        return SvgImage.CREDENTIALS;
    }

    /**
     * @return the icon colour for the content.
     */
    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    /**
     * @return the label for the content - 'App Store'
     */
    @Override
    public String getLabel() {
        return LABEL;
    }

    /**
     * @return the type of the content - 'App Store'
     */
    @Override
    public String getType() {
        return TAB_TYPE;
    }

    /**
     * Called by the selection model in the list to set the Credentials shown in the details view.
     */
    private void credentialsListSelectionHandler(@SuppressWarnings("unused") final MultiSelectEvent event) {
        final Credentials credentials = credentialsListPresenter.getSelectionModel().getSelected();
        credentialsDetailsPresenter.setCredentials(credentials);
    }

    /**
     * GWT view managed by this presenter.
     */
    public interface CredentialsView extends View {

        SimplePanel getDetailsPanel();
    }

}
