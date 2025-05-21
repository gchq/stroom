package stroom.appstore.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Represents the RHS of the UI when displaying the App Store.
 */
public class AppStorePresenter extends ContentTabPresenter<AppStorePresenter.AppStoreView>
implements Refreshable {

    /** Label for the content */
    private final static String LABEL = "App Store";

    /** Tab type for the content (what is this?) */
    private final static String TAB_TYPE = "App Store";

    /**
     * Injected constructor.
     * @param eventBus Passed to superclass
     * @param view Passed to superclass
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStorePresenter(final EventBus eventBus,
                             final AppStoreView view) {
        super(eventBus, view);
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
        // TODO Put in proper icon
        return SvgImage.QUESTION;
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
     * Called by GWT when the UI is refreshed
     */
    @Override
    public void refresh() {
        // TODO
    }

    /**
     * GWT view managed by this presenter.
     */
    public interface AppStoreView extends View {

    }
}
