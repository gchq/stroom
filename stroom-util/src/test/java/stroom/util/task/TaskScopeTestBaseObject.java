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

package stroom.util.task;

import stroom.util.concurrent.AtomicSequence;

public class TaskScopeTestBaseObject {
    private static AtomicSequence atomicSequence = new AtomicSequence();

    private final int instanceNumber = atomicSequence.next();
    private String name;

    public int getInstanceNumber() {
        return instanceNumber;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void trace(StringBuilder builder) {
        builder.append(this.getClass().getName() + " instance " + instanceNumber + " " + name + "\n");
    }
}
