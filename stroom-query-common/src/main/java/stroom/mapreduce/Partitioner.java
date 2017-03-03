/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.mapreduce;

/**
 * The job of the partitioner is to take output from the mapper and partition it
 * by key. Values with matching keys are gathered into collections so that a
 * reducer can reduce all items with a matching key. An instance of a
 * partitioner may create tasks for each key so that reducers can be run in
 * parallel either locally or across a cluster.
 *
 * @param <K2>
 * @param <V2>
 * @param <K3>
 * @param <V3>
 */
public interface Partitioner<K2, V2, K3, V3> extends Reader<K2, V2> {
    void partition();

    void setOutputCollector(OutputCollector<K3, V3> outputCollector);
}
