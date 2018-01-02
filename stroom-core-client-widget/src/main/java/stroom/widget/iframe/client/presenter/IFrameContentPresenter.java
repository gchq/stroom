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

package stroom.widget.iframe.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.cell.clickable.client.Hyperlink;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.widget.tab.client.presenter.TabData;

public class IFrameContentPresenter extends MyPresenterWidget<IFrameContentView> implements TabData {
    private Hyperlink hyperlink;
    private Icon icon = SvgPresets.EXPLORER;

    @Inject
    public IFrameContentPresenter(final EventBus eventBus, final IFrameContentView view) {
        super(eventBus, view);
    }

    public void close() {
        getView().cleanup();
    }

    public void setHyperlink(final Hyperlink hyperlink) {
        this.hyperlink = hyperlink;
        getView().setUrl(hyperlink.getHref());
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getLabel() {
        return (null != hyperlink) ? hyperlink.getTitle() : null;
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    public interface IFrameContentView extends View {
        void setUrl(String url);

        void cleanup();
    }

    public void setIcon(final Icon icon) {
        this.icon = icon;
    }
}