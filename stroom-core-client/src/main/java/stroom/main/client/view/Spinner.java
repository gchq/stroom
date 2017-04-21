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

package stroom.main.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import stroom.main.client.presenter.MainPresenter.SpinnerDisplay;
import stroom.widget.spinner.client.SpinnerWhite;

public class Spinner extends Composite implements SpinnerDisplay {
    interface Binder extends UiBinder<Widget, Spinner> {
    }

    private static Binder binder = GWT.create(Binder.class);

    private SpinnerWhite spinner;
    private boolean spinning;

    public Spinner() {
        initWidget(binder.createAndBindUi(this));
    }

    @Override
    public void start() {
        if (!spinning) {
            spinning = true;
            spinner = new SpinnerWhite();
            getElement().removeAllChildren();
            getElement().appendChild(spinner.getElement());
            spinner.setVisible(true);
        }
    }

    @Override
    public void stop() {
        if (spinning) {
            spinning = false;
            getElement().removeAllChildren();
            spinner = null;
        }
    }

    @Override
    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return addDomHandler(handler, ClickEvent.getType());
    }

    @Override
    public HandlerRegistration addDoubleClickHandler(final DoubleClickHandler handler) {
        return addDomHandler(handler, DoubleClickEvent.getType());
    }
}
