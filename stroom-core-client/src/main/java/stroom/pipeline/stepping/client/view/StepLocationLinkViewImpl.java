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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.widget.button.client.LinkButton;
import stroom.widget.presenter.client.ViewImpl;
import stroom.widget.util.client.DisabledState;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class StepLocationLinkViewImpl extends ViewImpl implements StepLocationLinkView {

    // A button showing the location, which opens the dialog that sets it
    private final LinkButton button;

    @Inject
    public StepLocationLinkViewImpl() {
        button = new LinkButton();
        button.addStyleName("stepLocationLink");
        button.setTitle("Set Location");
        button.setOpensDialog(true);
    }

    @Override
    public Widget asWidget() {
        return button;
    }

    @Override
    public LinkButton getButton() {
        return button;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        button.setStyleName("stepLocationLink--disabled", !enabled);
        // Disabled, it stays focusable so its name can still be found, and pressing it does nothing
        DisabledState.set(button.getElement(), !enabled);
    }

    public interface Binder extends UiBinder<Widget, StepLocationLinkViewImpl> {

    }
}
