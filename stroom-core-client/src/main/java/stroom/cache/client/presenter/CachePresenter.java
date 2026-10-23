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

package stroom.cache.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class CachePresenter extends ContentTabPresenter<CachePresenter.CacheView> {

    public static final String TAB_TYPE = "Caches";

    private final CacheListPresenter cacheListPresenter;
    private final CacheNodeListPresenter cacheNodeListPresenter;

    @Inject
    public CachePresenter(final EventBus eventBus,
                          final CacheView view,
                          final CacheListPresenter cacheListPresenter,
                          final CacheNodeListPresenter cacheNodeListPresenter) {
        super(eventBus, view);
        own(cacheListPresenter);
        own(cacheNodeListPresenter);
        this.cacheListPresenter = cacheListPresenter;
        this.cacheNodeListPresenter = cacheNodeListPresenter;

        getView().setCacheListView(cacheListPresenter.getView());
        getView().setCacheNodeListView(cacheNodeListPresenter.getView());

        own(
                cacheListPresenter.getSelectionModel().addSelectionHandler(event -> {
                    final String cacheName = cacheListPresenter.getSelectionModel()
                            .getSelected()
                            .getCacheName();
                    cacheNodeListPresenter.read(cacheName);
                }));
        // When a cache is cleared/evicted refresh the node list presenter
        cacheListPresenter.setCacheUpdateHandler(cacheNodeListPresenter::read);
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

    // --------------------------------------------------------------------------------


    public interface CacheView extends View {

        /// Shows the list of caches.
        ///
        /// @param view Its view.
        void setCacheListView(View view);

        /// Shows the selected cache on each node.
        ///
        /// @param view Its view.
        void setCacheNodeListView(View view);
    }
}
