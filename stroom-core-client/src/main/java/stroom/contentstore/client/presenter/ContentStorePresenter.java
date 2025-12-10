/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.contentstore.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter.ContentStoreView;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreResource;
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
public class ContentStorePresenter extends ContentTabPresenter<ContentStoreView> {

    /** Widget to show the list of available content packs */
    private final ContentStoreContentPackListPresenter contentPackListPresenter;

    /** Widget to show the details of a particular content pack */
    public final ContentStoreContentPackDetailsPresenter contentPackDetailsPresenter;

    /** Label for the content */
    private static final String LABEL = "Content Store";

    /** Tab type for the content (what is this?) */
    private static final String TAB_TYPE = "Content Store";

    /** ID of the presenter for the list of content packs */
    public static final String CONTENT_PACK_LIST = "CONTENT_PACK_LIST";

    /** Resource to access server-side data across the package */
    static final ContentStoreResource CONTENT_STORE_RESOURCE
            = GWT.create(ContentStoreResource.class);

    /**
     * Injected constructor.
     * @param eventBus Passed to superclass
     * @param view Passed to superclass
     * @param contentPackListPresenter The widget to show the list of available content packs.
     */
    @SuppressWarnings("unused")
    @Inject
    public ContentStorePresenter(final EventBus eventBus,
                                 final ContentStoreView view,
                                 final ContentStoreContentPackListPresenter contentPackListPresenter,
                                 final ContentStoreContentPackDetailsPresenter contentPackDetailsPresenter) {
        super(eventBus, view);
        this.contentPackListPresenter = contentPackListPresenter;
        this.contentPackListPresenter.setContentStorePresenter(this);
        this.contentPackDetailsPresenter = contentPackDetailsPresenter;
        this.contentPackDetailsPresenter.setContentStorePresenter(this);
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
        return SvgImage.CONTENT_STORE;
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
     * Handler for when a row is selected in the list of content packs.
     * Displays blank details if nothing is selected.
     * @param event The event (ignored - can be null)
     */
    private void contentPackListSelectionHandler(final MultiSelectEvent event) {
        final ContentStoreContentPackWithDynamicState cpws =
                contentPackListPresenter.getSelectionModel().getSelected();
        contentPackDetailsPresenter.setContentPack(cpws);
    }

    /**
     * GWT view managed by this presenter.
     */
    public interface ContentStoreView extends View {

        /**
         * Returns the details panel so stuff can be put into it
         */
        SimplePanel getContentPackDetailsPanel();
    }

    /**
     * Updates the state of the UI. Call this when something changes
     * in the state of a content pack.
     */
    public void updateState() {
        this.contentPackListPresenter.redraw();
        this.contentPackDetailsPresenter.setState();
    }

}
