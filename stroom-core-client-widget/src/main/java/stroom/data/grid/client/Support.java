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

package stroom.data.grid.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

import stroom.data.grid.client.DataGridViewImpl.Heading;

public abstract class Support {
    protected Heading getHeading(final Element tableElement, final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        int childIndex = -1;
        Element th = target;
        Element headerRow = null;

        // Get parent th.
        while (th != null && !"th".equalsIgnoreCase(th.getTagName())) {
            th = th.getParentElement();
        }

        if (th != null) {
            headerRow = th.getParentElement();
            if (headerRow != null) {
                childIndex = -1;
                for (int i = 0; i < headerRow.getChildCount(); i++) {
                    if (headerRow.getChild(i) == th) {
                        childIndex = i;
                        break;
                    }
                }

                return new Heading(tableElement, th, childIndex, event.getClientX());
            }
        }

        return null;
    }
}
