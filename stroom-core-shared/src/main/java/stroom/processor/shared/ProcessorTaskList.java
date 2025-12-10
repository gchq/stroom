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

package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ProcessorTaskList {

    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final List<ProcessorTask> list;

    @JsonCreator
    public ProcessorTaskList(@JsonProperty("nodeName") final String nodeName,
                             @JsonProperty("list") final List<ProcessorTask> list) {
        this.nodeName = nodeName;
        this.list = list;
    }

    public String getNodeName() {
        return nodeName;
    }

    public List<ProcessorTask> getList() {
        return list;
    }

    @Override
    public String toString() {
        return "ProcessorTaskList{" +
                "nodeName='" + nodeName + '\'' +
                ", list=" + list +
                '}';
    }
}
