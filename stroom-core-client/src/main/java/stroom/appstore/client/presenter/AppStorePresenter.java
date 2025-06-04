package stroom.appstore.client.presenter;

import stroom.appstore.shared.AppStoreContentPack;
import stroom.appstore.shared.AppStoreResource;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MultiSelectEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Represents the Content Tab of the UI when displaying the App Store.
 */
public class AppStorePresenter extends ContentTabPresenter<AppStorePresenter.AppStoreView>
implements Refreshable {

    /** Widget to show the list of available content packs */
    private final AppStoreContentPackListPresenter contentPackListPresenter;

    /** Widget to show the details of a particular content pack */
    public final AppStoreContentPackDetailsPresenter contentPackDetailsPresenter;

    /** Label for the content */
    private final static String LABEL = "App Store";

    /** Tab type for the content (what is this?) */
    private final static String TAB_TYPE = "App Store";

    /** ID of the presenter for the list of content packs */
    public final static String CONTENT_PACK_LIST = "CONTENT_PACK_LIST";

    /** Resource to access server-side data across the package */
    final static AppStoreResource APP_STORE_RESOURCE = GWT.create(AppStoreResource.class);

    /**
     * Injected constructor.
     * @param eventBus Passed to superclass
     * @param view Passed to superclass
     * @param contentPackListPresenter The widget to show the list of available content packs.
     */
    @SuppressWarnings("unused")
    @Inject
    public AppStorePresenter(final EventBus eventBus,
                             final AppStoreView view,
                             final AppStoreContentPackListPresenter contentPackListPresenter,
                             final AppStoreContentPackDetailsPresenter contentPackDetailsPresenter) {
        super(eventBus, view);
        this.contentPackListPresenter = contentPackListPresenter;
        this.contentPackDetailsPresenter = contentPackDetailsPresenter;
        this.setInSlot(CONTENT_PACK_LIST, contentPackListPresenter);
        view.getContentPackDetailsPanel().add(contentPackDetailsPresenter);
    }

    /**
     * GWT callback.
     */
    @Override
    protected void onBind() {
        super.onBind();

        // Add in the handler for the list selection
        contentPackListPresenter.getSelectionModel()
                .addSelectionHandler(this::contentPackListSelectionHandler);
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
        contentPackListPresenter.refresh();
    }

    /**
     * Handler for when a row is selected in the list of content packs.
     * Displays blank details if nothing is selected.
     * @param event The event (ignored - can be null)
     */
    private void contentPackListSelectionHandler(final MultiSelectEvent event) {
        AppStoreContentPack cp =
                contentPackListPresenter.getSelectionModel().getSelected();
        contentPackDetailsPresenter.setContentPack(cp);
    }
    
    /**
     * GWT view managed by this presenter.
     */
    public interface AppStoreView extends View {

        /**
         * Returns the details panel so stuff can be put into it
         */
        SimplePanel getContentPackDetailsPanel();
    }
}
