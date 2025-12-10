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

package stroom.widget.spinner.client;

import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public class SpinnerSmall extends Widget implements TaskMonitorFactory {

    private int taskCount;

    public SpinnerSmall() {
        final Element spinningInner = DOM.createDiv();
        spinningInner.setClassName("spinning-inner");
        spinningInner.setInnerHTML(SvgImage.SPINNER_SPINNING.getSvg());
        spinningInner.setTitle("Loading...");
        final Element spinningOuter = DOM.createDiv();
        spinningOuter.appendChild(spinningInner);
        spinningOuter.setClassName("spinning-outer");
        final Element spinnerSmall = DOM.createDiv();
        spinnerSmall.appendChild(spinningOuter);
        spinnerSmall.setClassName("SpinnerSmall");
        setElement(spinnerSmall);
    }

    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            addStyleName("refreshing");
        } else {
            removeStyleName("refreshing");
        }
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                setRefreshing(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;

                if (taskCount < 0) {
                    GWT.log("Negative task count");
                }

                setRefreshing(taskCount > 0);
            }
        };
    }
}
