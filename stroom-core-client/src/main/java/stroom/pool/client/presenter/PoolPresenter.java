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

package stroom.pool.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.pool.shared.PoolRow;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.tab.client.presenter.Icon;

public class PoolPresenter extends ContentTabPresenter<PoolPresenter.PoolView> {
    public static final String LIST = "LIST";
    public static final String NODE_LIST = "NODE_LIST";

    public interface PoolView extends View {
    }

    private final PoolListPresenter poolListPresenter;
    private final PoolNodeListPresenter poolNodeListPresenter;

    @Inject
    public PoolPresenter(final EventBus eventBus, final PoolView view, final PoolListPresenter poolListPresenter,
            final PoolNodeListPresenter poolNodeListPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.poolListPresenter = poolListPresenter;
        this.poolNodeListPresenter = poolNodeListPresenter;

        setInSlot(LIST, poolListPresenter);
        setInSlot(NODE_LIST, poolNodeListPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(
                poolListPresenter.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
                    @Override
                    public void onSelectionChange(final SelectionChangeEvent event) {
                        final PoolRow row = poolListPresenter.getSelectionModel().getSelectedObject();
                        poolNodeListPresenter.read(row);
                    }
                }));
    }

    @Override
    public Icon getIcon() {
        return GlyphIcons.MONITORING;
    }

    @Override
    public String getLabel() {
        return "Pools";
    }
}
