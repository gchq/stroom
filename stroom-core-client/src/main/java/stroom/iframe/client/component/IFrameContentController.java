/*
 * Copyright 2016 Crown Copyright
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

package stroom.iframe.client.component;

import stroom.content.client.event.RefreshContentTabEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.LayerController;
import stroom.widget.tab.client.TabData;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows a page, from a hyperlink, as one of Stroom's tabs, named for the page's title.
public class IFrameContentController extends LayerController implements TabData {

    public static final String TAB_TYPE = "IFrame";

    private final IFrameContent view;
    private SvgImage icon = SvgImage.DEPENDENCIES;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     Its component.
    @Inject
    public IFrameContentController(final EventBus eventBus, final IFrameContent view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnTitleChange(() -> RefreshContentTabEvent.fire(this, this));
        // The page's frame is outside the tab, so it is removed when the tab is closed
        onDispose(view::cleanup);
    }

    /// @param url The page to show.
    public void setUrl(final String url) {
        view.setUrl(url);
    }

    /// @param customTitle The tab's name, in place of the page's title, or null for the page's.
    public void setCustomTitle(final String customTitle) {
        view.setCustomTitle(customTitle);
    }

    /// @param icon The tab's icon, or null to keep the one it has.
    public void setIcon(final SvgImage icon) {
        if (icon != null) {
            this.icon = icon;
        }
    }

    @Override
    public SvgImage getIcon() {
        return icon;
    }

    @Override
    public String getLabel() {
        return view.getTitle();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
