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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.SplitLayoutConfig;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

public class Splitter extends Widget {

    private final SplitInfo splitInfo;

    public Splitter(final SplitInfo splitInfo) {
        this.splitInfo = splitInfo;

        final Element element = DOM.createDiv();
        element.setClassName("flexLayout-splitter");

        if (splitInfo.layoutConfig.getDimension() == Dimension.X) {
            element.addClassName("flexLayout-splitterAcross");
        } else {
            element.addClassName("flexLayout-splitterDown");
        }

        final Element dragger = DOM.createDiv();
        dragger.setClassName("flexLayout-splitter-dragger");
        element.appendChild(dragger);

        setElement(element);
    }

    public SplitInfo getSplitInfo() {
        return splitInfo;
    }

    public static class SplitInfo {
        private final SplitLayoutConfig layoutConfig;
        private final int index;

        public SplitInfo(final SplitLayoutConfig layoutConfig, final int index) {
            this.layoutConfig = layoutConfig;
            this.index = index;
        }

        public SplitLayoutConfig getLayoutConfig() {
            return layoutConfig;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SplitInfo splitInfo = (SplitInfo) o;
            return index == splitInfo.index && Objects.equals(layoutConfig, splitInfo.layoutConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layoutConfig, index);
        }
    }
}
