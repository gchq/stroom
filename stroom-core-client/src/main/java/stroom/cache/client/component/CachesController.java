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

package stroom.cache.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the caches, and the selected cache on each node; clearing a cache shows its nodes again.
public class CachesController extends ContentTabController {

    public static final String TAB_TYPE = "Caches";

    private final Caches view;
    private final CacheListController cacheList;
    private final CacheNodeListController cacheNodeList;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public CachesController(final EventBus eventBus,
                            final Caches view,
                            final CacheListController cacheList,
                            final CacheNodeListController cacheNodeList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(cacheList);
        own(cacheNodeList);
        this.cacheList = cacheList;
        this.cacheNodeList = cacheNodeList;

        view.setCacheListView(cacheList);
        view.setCacheNodeListView(cacheNodeList);

        own(
                cacheList.getSelectionModel().addSelectionHandler(event -> {
                    final String cacheName = cacheList.getSelectionModel()
                            .getSelected()
                            .getCacheName();
                    cacheNodeList.read(cacheName);
                }));
        // When a cache is cleared/evicted refresh the node list
        cacheList.setOnCacheCleared(cacheNodeList::read);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.MONITORING;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Caches";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
