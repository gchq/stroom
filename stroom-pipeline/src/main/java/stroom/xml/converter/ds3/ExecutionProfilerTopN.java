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

package stroom.xml.converter.ds3;

import stroom.util.shared.CompareUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionProfilerTopN {
    List<ExecutionProfiler> topN = new ArrayList<>();

    public ExecutionProfilerTopN(Root root, int n) {
        buildExecutionProfilerList(topN, root);
        init(n);
    }

    public ExecutionProfilerTopN(List<ExecutionProfiler> list, int n) {
        topN = list;
        init(n);
    }

    public List<ExecutionProfiler> getTopN() {
        return topN;
    }

    private void init(int n) {
        sort();
        if (topN.size() > n) {
            topN = topN.subList(0, n);
        }
    }

    private void sort() {
        Collections.sort(topN,
                (lhs, rhs) -> CompareUtil.compareLong(rhs.getTotalExecutionTime(), lhs.getTotalExecutionTime()));
    }

    private void buildExecutionProfilerList(List<ExecutionProfiler> list, Node node) {
        if (node instanceof ExecutionProfiler) {
            list.add((ExecutionProfiler) node);
        }
        if (node.getChildNodes() != null) {
            for (Node child : node.getChildNodes()) {
                buildExecutionProfilerList(list, child);
            }
        }
    }

}
