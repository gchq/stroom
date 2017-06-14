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

package stroom.data.pager.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import stroom.widget.button.client.SvgButton;
import stroom.widget.button.client.SvgIcons;

public class SmallSpinner extends Composite {
    public interface Style extends CssResource {
        String smallSpinner();

        String spinning();
    }

    public interface Resources extends ClientBundle {
        @Source("SmallSpinner.css")
        Style style();
    }

    private static Resources resources;

    public SmallSpinner() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        final SvgButton button = SvgButton.create(SvgIcons.SPINNER);
        initWidget(button);

        getElement().setClassName(resources.style().smallSpinner());
    }

    public void setSpinning(final boolean spinning) {
        if (spinning) {
            getElement().addClassName(resources.style().spinning());
        } else {
            getElement().removeClassName(resources.style().spinning());
        }
    }
}
