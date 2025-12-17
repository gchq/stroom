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

package stroom.cache.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class CachePresenter extends ContentTabPresenter<CachePresenter.CacheView> {

    public static final String TAB_TYPE = "Caches";
    public static final String LIST = "LIST";
    public static final String NODE_LIST = "NODE_LIST";

    private final CacheListPresenter cacheListPresenter;
    private final CacheNodeListPresenter cacheNodeListPresenter;

    @Inject
    public CachePresenter(final EventBus eventBus,
                          final CacheView view,
                          final CacheListPresenter cacheListPresenter,
                          final CacheNodeListPresenter cacheNodeListPresenter) {
        super(eventBus, view);
        this.cacheListPresenter = cacheListPresenter;
        this.cacheNodeListPresenter = cacheNodeListPresenter;

        setInSlot(LIST, cacheListPresenter);
        setInSlot(NODE_LIST, cacheNodeListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(
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

    }
}
