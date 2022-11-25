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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public class Splitter extends Widget {

    private final SplitInfo splitInfo;
    private final Element element;

    public Splitter(final SplitInfo splitInfo) {
        this.splitInfo = splitInfo;

        element = DOM.createDiv();
        element.setClassName("flexLayout-splitter");

        if (splitInfo.layoutConfig.getDimension() == Dimension.X) {
            element.addClassName("flexLayout-splitterAcross");
        } else {
            element.addClassName("flexLayout-splitterDown");
        }

        setElement(element);
    }

    public SplitInfo getSplitInfo() {
        return splitInfo;
    }

    public static class SplitInfo {

        // private Widget widget;
        private SplitLayoutConfig layoutConfig;
        private int index;

        public SplitInfo(final SplitLayoutConfig layoutConfig, final int index) {
            this.layoutConfig = layoutConfig;
            this.index = index;
        }

        @Override
        public int hashCode() {
            final HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(layoutConfig);
            builder.append(index);
            return builder.toHashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (o == null || !(o instanceof SplitInfo)) {
                return false;
            }

            final SplitInfo splitInfo = (SplitInfo) o;
            final EqualsBuilder builder = new EqualsBuilder();
            builder.append(layoutConfig, splitInfo.layoutConfig);
            builder.append(index, splitInfo.index);
            return builder.isEquals();
        }

        public SplitLayoutConfig getLayoutConfig() {
            return layoutConfig;
        }

        public int getIndex() {
            return index;
        }
    }
}
