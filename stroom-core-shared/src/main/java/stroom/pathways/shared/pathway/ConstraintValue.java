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

package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AnyTypeValue.class, name = "anyValue"),
        @JsonSubTypes.Type(value = NanoTimeValue.class, name = "duration"),
        @JsonSubTypes.Type(value = NanoTimeRange.class, name = "durationRange"),
        @JsonSubTypes.Type(value = StringValue.class, name = "string"),
        @JsonSubTypes.Type(value = StringSet.class, name = "stringSet"),
        @JsonSubTypes.Type(value = Regex.class, name = "regex"),
        @JsonSubTypes.Type(value = BooleanValue.class, name = "boolean"),
        @JsonSubTypes.Type(value = AnyBoolean.class, name = "booleanSet"),
        @JsonSubTypes.Type(value = IntegerValue.class, name = "integer"),
        @JsonSubTypes.Type(value = IntegerSet.class, name = "integerSet"),
        @JsonSubTypes.Type(value = IntegerRange.class, name = "integerRange"),
        @JsonSubTypes.Type(value = LongValue.class, name = "long"),
        @JsonSubTypes.Type(value = LongSet.class, name = "longSet"),
        @JsonSubTypes.Type(value = LongRange.class, name = "longRange"),
        @JsonSubTypes.Type(value = DoubleValue.class, name = "double"),
        @JsonSubTypes.Type(value = DoubleSet.class, name = "doubleSet"),
        @JsonSubTypes.Type(value = DoubleRange.class, name = "doubleRange")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "anyValue", schema = AnyTypeValue.class),
                @DiscriminatorMapping(value = "duration", schema = NanoTimeValue.class),
                @DiscriminatorMapping(value = "durationRange", schema = NanoTimeRange.class),
                @DiscriminatorMapping(value = "string", schema = StringValue.class),
                @DiscriminatorMapping(value = "stringSet", schema = StringSet.class),
                @DiscriminatorMapping(value = "regex", schema = Regex.class),
                @DiscriminatorMapping(value = "boolean", schema = BooleanValue.class),
                @DiscriminatorMapping(value = "booleanSet", schema = AnyBoolean.class),
                @DiscriminatorMapping(value = "integer", schema = IntegerValue.class),
                @DiscriminatorMapping(value = "integerSet", schema = IntegerSet.class),
                @DiscriminatorMapping(value = "integerRange", schema = IntegerRange.class),
                @DiscriminatorMapping(value = "long", schema = LongValue.class),
                @DiscriminatorMapping(value = "longSet", schema = LongSet.class),
                @DiscriminatorMapping(value = "longRange", schema = LongRange.class),
                @DiscriminatorMapping(value = "double", schema = DoubleValue.class),
                @DiscriminatorMapping(value = "doubleSet", schema = DoubleSet.class),
                @DiscriminatorMapping(value = "doubleRange", schema = DoubleRange.class)})
public sealed interface ConstraintValue permits
        AnyTypeValue,
        NanoTimeValue,
        NanoTimeRange,
        StringValue,
        StringSet,
        Regex,
        BooleanValue,
        AnyBoolean,
        IntegerValue,
        IntegerSet,
        IntegerRange,
        LongValue,
        LongSet,
        LongRange,
        DoubleValue,
        DoubleSet,
        DoubleRange {

    ConstraintValueType valueType();
}
