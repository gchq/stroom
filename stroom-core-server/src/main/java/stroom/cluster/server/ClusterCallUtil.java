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

package stroom.cluster.server;

import stroom.node.shared.Node;
import stroom.util.shared.ModelStringUtil;

public abstract class ClusterCallUtil {
    public static String logString(final String prefix, final Node sourceNode, final Node targetNode,
                                   final String beanName, final String methodName, final Long ms) {
        return prefix + " " + sourceNode.getName() + "->" + targetNode.getName() + " - " + beanName + "." + methodName
                + " took " + ModelStringUtil.formatDurationString(ms);
    }

    public static String logString(final String prefix, final Node sourceNode, final Node targetNode,
                                   final String beanName, final String methodName) {
        return prefix + " " + sourceNode.getName() + "->" + targetNode.getName() + " - " + beanName + "." + methodName;
    }
}
