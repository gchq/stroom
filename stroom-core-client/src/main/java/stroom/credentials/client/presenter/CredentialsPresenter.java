package stroom.credentials.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Represents the Credentials Tab of the UI.
 */
public class CredentialsPresenter
        extends ContentTabPresenter<CredentialsPresenter.CredentialsView> {

    /** Label for the content */
    private static final String LABEL = "Credentials";

    /** Tab type for the content (what is this?) */
    private static final String TAB_TYPE = "Credentials";

    /**
     * Injected constructor.
     * @param eventBus Passed to superclass
     * @param view Passed to superclass
     * @param credentialsListPresenter The list of credentials to show within this top-level tab.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsPresenter(final EventBus eventBus,
                                final CredentialsView view,
                                final CredentialsListPresenter credentialsListPresenter) {
        super(eventBus, view);
        credentialsListPresenter.setParentPresenter(this);
        this.setInSlot(CredentialsListPresenter.CREDENTIALS_LIST, credentialsListPresenter);
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
        return SvgImage.KEY_BLUE;
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
        // No code
    }

}
