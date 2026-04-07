/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.dashboard.client.main;

import stroom.dashboard.client.flexlayout.MutableLayoutConfig;
import stroom.dashboard.client.flexlayout.MutableSize;
import stroom.dashboard.client.flexlayout.MutableSplitLayoutConfig;
import stroom.dashboard.client.flexlayout.MutableTabConfig;
import stroom.dashboard.client.flexlayout.MutableTabLayoutConfig;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.stream.Collectors;

public class MutableConfigUtil {

    public static LayoutConfig toLayoutConfig(final MutableLayoutConfig layoutConfig) {
        if (layoutConfig == null) {
            return null;
        }
        if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
            return new SplitLayoutConfig(
                    toSize(splitLayoutConfig.getPreferredSize()),
                    splitLayoutConfig.getDimension(),
                    toLayoutConfigList(splitLayoutConfig.getChildren()));

        } else if (layoutConfig instanceof final MutableTabLayoutConfig tabLayoutConfig) {
            return new TabLayoutConfig(
                    toSize(tabLayoutConfig.getPreferredSize()),
                    toTabConfigList(tabLayoutConfig.getTabs()),
                    tabLayoutConfig.getSelected());
        }
        return null;
    }

    public static Size toSize(final MutableSize size) {
        if (size == null) {
            return null;
        }
        return new Size(size.getWidth(), size.getHeight());
    }

    public static List<LayoutConfig> toLayoutConfigList(final List<MutableLayoutConfig> list) {
        if (NullSafe.isEmptyCollection(list)) {
            return null;
        }
        return list.stream().map(MutableConfigUtil::toLayoutConfig).collect(Collectors.toList());
    }

    public static List<TabConfig> toTabConfigList(final List<MutableTabConfig> list) {
        if (NullSafe.isEmptyCollection(list)) {
            return null;
        }
        return list.stream().map(MutableConfigUtil::toTabConfig).collect(Collectors.toList());
    }

    public static TabConfig toTabConfig(final MutableTabConfig tabConfig) {
        if (tabConfig == null) {
            return null;
        }
        return new TabConfig(tabConfig.getId(), tabConfig.isVisible() ? null : tabConfig.isVisible());
    }

    public static MutableLayoutConfig fromLayoutConfig(final LayoutConfig layoutConfig) {
        if (layoutConfig == null) {
            return null;
        }
        if (layoutConfig instanceof final SplitLayoutConfig splitLayoutConfig) {
            final MutableSplitLayoutConfig mutableSplitLayoutConfig = new MutableSplitLayoutConfig(
                    fromSize(splitLayoutConfig.getPreferredSize()),
                    splitLayoutConfig.getDimension());
            if (!NullSafe.isEmptyCollection(splitLayoutConfig.getChildren())) {
                splitLayoutConfig.getChildren().forEach(child ->
                        mutableSplitLayoutConfig.add(fromLayoutConfig(child)));
            }
            return mutableSplitLayoutConfig;

        } else if (layoutConfig instanceof final TabLayoutConfig tabLayoutConfig) {
            final MutableTabLayoutConfig mutableTabLayoutConfig = new MutableTabLayoutConfig(
                    fromSize(tabLayoutConfig.getPreferredSize()),
                    tabLayoutConfig.getSelected());
            if (!NullSafe.isEmptyCollection(tabLayoutConfig.getTabs())) {
                tabLayoutConfig.getTabs().forEach(child ->
                        mutableTabLayoutConfig.add(fromTabConfig(child)));
            }
            return mutableTabLayoutConfig;
        }
        return null;
    }

    public static MutableSize fromSize(final Size size) {
        if (size == null) {
            return null;
        }
        return new MutableSize(size.getWidth(), size.getHeight());
    }

    public static MutableTabConfig fromTabConfig(final TabConfig tabConfig) {
        if (tabConfig == null) {
            return null;
        }
        return new MutableTabConfig(tabConfig.getId(), tabConfig.getVisible() == null || tabConfig.getVisible());
    }
}
