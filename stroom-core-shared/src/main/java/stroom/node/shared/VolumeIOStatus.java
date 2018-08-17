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

package stroom.node.shared;

import stroom.docref.SharedObject;

public class VolumeIOStatus implements SharedObject {
    private static final long serialVersionUID = 1727071475174943132L;
    private VolumeEntity volume;
    private String path;
    private String readSpeed;
    private String writeSpeed;

    public String getReadSpeed() {
        return readSpeed;
    }

    public void setReadSpeed(String readSpeed) {
        this.readSpeed = readSpeed;
    }

    public String getWriteSpeed() {
        return writeSpeed;
    }

    public void setWriteSpeed(String writeSpeed) {
        this.writeSpeed = writeSpeed;
    }

    public VolumeEntity getVolume() {
        return volume;
    }

    public void setVolume(VolumeEntity volume) {
        this.volume = volume;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
