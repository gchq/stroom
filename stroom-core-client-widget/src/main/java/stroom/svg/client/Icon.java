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

package stroom.svg.client;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class Icon {

    protected final String className;

    protected Icon(final String className) {
        this.className = className;
    }

    public static Icon create(final String className) {
        if (className == null) {
            return null;
        }
        return new Icon(className);
    }

    public String getClassName() {
        return "svgIcon " + className;
    }

    public Widget asWidget() {
        final SimplePanel panel = new SimplePanel();
        panel.getElement().addClassName("svgIcon");
        panel.getElement().addClassName(className);
        return panel;
    }
}
