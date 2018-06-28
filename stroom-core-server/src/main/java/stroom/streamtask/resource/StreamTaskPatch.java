/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.streamtask.resource;

public class StreamTaskPatch {

    private String op;
    private String path;
    private String value;

    public String getOp() {
        return op;
    }

    public StreamTaskPatch setOp(String op) {
        this.op = op;
        return this;
    }

    public String getPath() {
        return path;
    }

    public StreamTaskPatch setPath(String path) {
        this.path = path;
        return this;
    }

    public String getValue() {
        return value;
    }

    public StreamTaskPatch setValue(String value) {
        this.value = value;
        return this;
    }
}
