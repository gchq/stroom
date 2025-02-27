/*
 * Copyright 2018 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.DurationPresenter.DurationView;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.customdatebox.client.DurationPicker;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DurationViewImpl extends ViewImpl implements DurationView {

    private final DurationPicker widget;

    @Inject
    public DurationViewImpl() {
        widget = new DurationPicker();
    }

    @Override
    public void setDuration(final SimpleDuration duration) {
        widget.setValue(duration);
    }

    @Override
    public SimpleDuration getDuration() {
        return widget.getValue();
    }

    @Override
    public void focus() {
        widget.focus();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }
}
