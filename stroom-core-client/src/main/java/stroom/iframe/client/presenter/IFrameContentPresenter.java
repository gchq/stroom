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

package stroom.iframe.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.tab.client.presenter.TabData;

public class IFrameContentPresenter extends MyPresenterWidget<IFrameContentPresenter.IFrameContentView> implements TabData, IFrameLoadUiHandlers {
    private Icon icon = SvgPresets.LINK;

    @Inject
    public IFrameContentPresenter(final EventBus eventBus, final IFrameContentView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void close() {
        getView().cleanup();
    }

    public void setUrl(final String url) {
        getView().setUrl(url);
    }

    public void setCustomTitle(final String customTitle) {
        getView().setCustomTitle(customTitle);
    }

    public void setIcon(final Icon icon) {
        if (icon != null) {
            this.icon = icon;
        }
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getLabel() {
        return getView().getTitle();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public void onTitleChange(final String title) {
        RefreshContentTabEvent.fire(this, this);
    }

    public interface IFrameContentView extends View, HasUiHandlers<IFrameLoadUiHandlers> {
        void setUrl(String url);

        void setCustomTitle(String customTitle);

        String getTitle();

        void cleanup();
    }
}