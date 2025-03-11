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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "size")
@XmlType(name = "Size", propOrder = {"width", "height"})
@JsonPropertyOrder({"width", "height"})
@JsonInclude(Include.NON_NULL)
public class Size {

    @XmlElement(name = "width")
    @JsonProperty("width")
    private int width;
    @XmlElement(name = "height")
    @JsonProperty("height")
    private int height;

    public Size() {
    }

    @JsonCreator
    public Size(@JsonProperty("width") final int width,
                @JsonProperty("height") final int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(final int height) {
        this.height = height;
    }

    public void set(final int dimension, final int size) {
        if (dimension == 0) {
            width = size;
        } else {
            height = size;
        }
    }

    public int get(final int dimension) {
        if (dimension == 0) {
            return width;
        }
        return height;
    }

    @Override
    public String toString() {
        return "[" + width + ", " + height + "]";
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int width;
        private int height;

        private Builder() {
        }

        private Builder(final Size size) {
            this.width = size.width;
            this.height = size.height;
        }

        public Builder width(final int width) {
            this.width = width;
            return this;
        }

        public Builder height(final int height) {
            this.height = height;
            return this;
        }

        public Size build() {
            return new Size(width, height);
        }
    }
}
