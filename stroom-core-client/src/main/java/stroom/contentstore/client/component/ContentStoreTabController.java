/*
 * Copyright 2025 Crown Copyright
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

package stroom.contentstore.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MultiSelectEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the content packs and the selected pack's details.
public class ContentStoreTabController extends ContentTabController {

    public static final String TAB_TYPE = "Content Store";

    private static final String LABEL = "Content Store";

    private final ContentStoreContentPackListController contentPackList;
    private final ContentStoreContentPackDetailsController contentPackDetails;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               Its component.
    /// @param contentPackList    The content pack list.
    /// @param contentPackDetails The content pack details.
    @Inject
    public ContentStoreTabController(final EventBus eventBus,
                                     final ContentStoreTab view,
                                     final ContentStoreContentPackListController contentPackList,
                                     final ContentStoreContentPackDetailsController contentPackDetails) {
        super(eventBus);
        initWidget(view);
        this.contentPackList = own(contentPackList).attach(view.getContentPackList());
        this.contentPackDetails = own(contentPackDetails).attach(view.getContentPackDetails());
        contentPackList.setOnStateChange(this::updateState);
        contentPackDetails.setOnInstalled(this::updateState);

        own(contentPackList.getSelectionModel().addSelectionHandler(this::onContentPackSelected));
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.CONTENT_STORE;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    /// Shows the selected pack's details, or none when no pack is selected.
    private void onContentPackSelected(final MultiSelectEvent event) {
        final ContentStoreContentPackWithDynamicState cpws = contentPackList.getSelectionModel().getSelected();
        contentPackDetails.setContentPack(cpws);
    }

    /// Updates the list and the details. Call this when something changes in the state of a content pack.
    public void updateState() {
        this.contentPackList.redraw();
        this.contentPackDetails.updateState();
    }
}
