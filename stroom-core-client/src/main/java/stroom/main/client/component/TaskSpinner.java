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

package stroom.main.client.component;

import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/// The main toolbar's spinner, shown while the app's tasks run (after a short delay, so that quick
/// polls don't make it flicker); clicking it opens the task manager.
public class TaskSpinner extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SpinnerLarge spinner;

    private boolean spinning;
    private Runnable onClick;

    private final Timer timer;

    public TaskSpinner() {
        initWidget(BINDER.createAndBindUi(this));
        addDomHandler(event -> {
            if (onClick != null) {
                onClick.run();
            }
        }, ClickEvent.getType());

        timer = new Timer() {
            @Override
            public void run() {
                startSpinner();
            }
        };
    }

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

    public void stop() {
        if (timer.isRunning()) {
            timer.cancel();
        }
        if (spinning) {
            spinning = false;
            spinner.setVisible(false);
        }
    }

    /// @param onClick Run when the spinner is clicked (e.g. to open the task manager), or null for
    ///                nothing.
    public void setOnClick(final Runnable onClick) {
        this.onClick = onClick;
    }

    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return addDomHandler(handler, ClickEvent.getType());
    }

    public HandlerRegistration addDoubleClickHandler(final DoubleClickHandler handler) {
        return addDomHandler(handler, DoubleClickEvent.getType());
    }

    interface Binder extends UiBinder<Widget, TaskSpinner> {

    }
}
