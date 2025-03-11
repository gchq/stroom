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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Arrays;

@XmlType(name = "size", propOrder = {"width", "height"})
@Deprecated
public class Size implements SharedObject {

    private static final long serialVersionUID = 8201392610412513780L;

    @XmlTransient
    private final int[] size = new int[]{200, 200};

    public Size() {
        // Default constructor necessary for GWT serialisation.
    }

    @XmlElement(name = "width")
    public int getWidth() {
        return size[0];
    }

    public void setWidth(final int width) {
        size[0] = width;
    }

    @XmlElement(name = "height")
    public int getHeight() {
        return size[1];
    }

    public void setHeight(final int height) {
        size[1] = height;
    }

    public void set(final int dimension, final int size) {
        this.size[dimension] = size;
    }

    public void set(final int[] size) {
        this.size[0] = size[0];
        this.size[1] = size[1];
    }

    public int get(final int dimension) {
        return this.size[dimension];
    }

    public int[] get() {
        return size;
    }

    @Override
    public String toString() {
        return Arrays.toString(size);
    }
}
