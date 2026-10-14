/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiTabPresenter.AskStroomAiTabView;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.svg.shared.SvgImage;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * The 'Ask Stroom AI' chat in a content tab (tab mode). The chat itself is shown by {@link AskStroomAiPresenter};
 * this tab only holds it, so the same chat can move between the panel, a dialog and a tab without losing anything.
 */
public class AskStroomAiTabPresenter extends ContentTabPresenter<AskStroomAiTabView> {

    public static final String TAB_TYPE = "AskStroomAi";

    private String label = "Ask Stroom AI";

    @Inject
    public AskStroomAiTabPresenter(final EventBus eventBus, final AskStroomAiTabView view) {
        super(eventBus, view);
    }

    /**
     * @param content The chat to show in the tab.
     */
    public void setContent(final Widget content) {
        getView().setContent(content);
    }

    /**
     * @param chatTitle The current chat's title, shown in the tab's label, or null.
     */
    public void setChatTitle(final String chatTitle) {
        label = chatTitle == null || chatTitle.isEmpty()
                ? "Ask Stroom AI"
                : "Ask Stroom AI: " + chatTitle;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.AI;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public interface AskStroomAiTabView extends View {

        void setContent(Widget content);
    }
}
