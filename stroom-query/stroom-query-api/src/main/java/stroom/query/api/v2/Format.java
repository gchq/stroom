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

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({"type", "numberFormat", "dateTimeFormat"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "Format", propOrder = {"type", "numberFormat", "dateTimeFormat"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "Describes the formatting that will be applied to values in a field")
public final class Format implements Serializable {
    private static final long serialVersionUID = -5380825645719299089L;

    @XmlElement
    @ApiModelProperty(
            value = "The formatting type to apply",
            example = "NUMBER",
            required = true)
    @JsonProperty
    private Type type;

    @XmlElement
    @JsonProperty
    private NumberFormat numberFormat;

    @XmlElement
    @JsonProperty
    private DateTimeFormat dateTimeFormat;

    public Format() {
    }

    public Format(final Type type) {
        this.type = type;
    }

    public Format(final NumberFormat numberFormat) {
        this.type = Type.NUMBER;
        this.numberFormat = numberFormat;
    }

    public Format(final DateTimeFormat dateTimeFormat) {
        this.type = Type.DATE_TIME;
        this.dateTimeFormat = dateTimeFormat;
    }

    @JsonCreator
    public Format(@JsonProperty("type") final Type type,
                  @JsonProperty("numberFormat") final NumberFormat numberFormat,
                  @JsonProperty("dateTimeFormat") final DateTimeFormat dateTimeFormat) {
        this.type = type;
        this.numberFormat = numberFormat;
        this.dateTimeFormat = dateTimeFormat;
    }

    public Type getType() {
        return type;
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public NumberFormat getNumberFormat() {
        return numberFormat;
    }

    public void setNumberFormat(final NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    public DateTimeFormat getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(final DateTimeFormat dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Format format = (Format) o;
        return type == format.type &&
                Objects.equals(numberFormat, format.numberFormat) &&
                Objects.equals(dateTimeFormat, format.dateTimeFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, numberFormat, dateTimeFormat);
    }

    @Override
    public String toString() {
        return "Format{" +
                "type=" + type +
                ", numberFormat=" + numberFormat +
                ", dateTimeFormat=" + dateTimeFormat +
                '}';
    }

    public enum Type implements HasDisplayValue {
        GENERAL("General"),
        NUMBER("Number"),
        DATE_TIME("Date Time"),
        TEXT("Text");

        private final String displayValue;

        Type(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    /**
     * Builder for constructing a {@link Format}
     */
    public static class Builder {
        private Type type;

        private NumberFormat numberFormat;

        private DateTimeFormat dateTimeFormat;

        public Builder() {
        }

        public Builder(final Format format) {
            this.type = format.type;
            if (format.numberFormat != null) {
                this.numberFormat = new NumberFormat.Builder(format.numberFormat).build();
            }
            if (format.dateTimeFormat != null) {
                this.dateTimeFormat = format.dateTimeFormat;
            }
        }

        /**
         * @param value XXXXXXXXXXXXXXXX
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder type(final Type value) {
            this.type = value;
            return this;
        }

        public NumberFormat.Builder number() {
            this.type = Type.NUMBER;
            return new NumberFormat.Builder();
        }

        public Builder number(final NumberFormat value) {
            this.numberFormat = value;
            this.type = Type.NUMBER;
            return this;
        }

        public DateTimeFormat.Builder dateTime() {
            this.type = Type.DATE_TIME;
            return new DateTimeFormat.Builder();
        }

        public Builder dateTime(final DateTimeFormat value) {
            this.dateTimeFormat = value;
            this.type = Type.DATE_TIME;
            return this;
        }

        public Format build() {
            return new Format(type, numberFormat, dateTimeFormat);
        }
    }
}