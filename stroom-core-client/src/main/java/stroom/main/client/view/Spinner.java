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

package stroom.main.client.view;

import stroom.main.client.presenter.MainPresenter.SpinnerDisplay;
import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class Spinner extends Composite implements SpinnerDisplay {

    private static final Binder binder = GWT.create(Binder.class);
    private final SpinnerLarge spinner;
    private boolean spinning;

    private final Timer timer;

    public Spinner() {
        initWidget(binder.createAndBindUi(this));
        spinner = new SpinnerLarge();
        spinner.setSoft(true);
        spinner.setVisible(false);
        getElement().appendChild(spinner.getElement());

        timer = new Timer() {
            @Override
            public void run() {
                startSpinner();
            }
        };
    }

    @Override
    public void start() {
        // The search polling hits this method on every poll and if we start the spinner immediately
        // then the CPU is caned constantly updating the DOM. Add a small delay to give the polling
        // a chance to finish and cancel the scheduler before the scheduled time is reached.
        if (!spinning && !timer.isRunning()) {
            timer.schedule(200);
        }
    }

    private void startSpinner() {
        spinning = true;
        spinner.setVisible(true);
    }

    @Override
    public void stop() {
        if (timer.isRunning()) {
            timer.cancel();
        }
        if (spinning) {
            spinning = false;
            spinner.setVisible(false);
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

    interface Binder extends UiBinder<Widget, Spinner> {

    }
}
