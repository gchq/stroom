package stroom.credentials.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.credentials.shared.CredentialsResource;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Represents the Credentials Tab of the UI.
 */
public class CredentialsPresenter extends ContentTabPresenter<CredentialsPresenter.CredentialsView> {

    private final CredentialsListPresenter credentialsListPresenter;

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
     * GWT view managed by this presenter.
     */
    public interface CredentialsView extends View {
        SimplePanel getDetailsPanel();
    }

}
