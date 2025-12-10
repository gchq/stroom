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

package stroom.test.common.data;

import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Class to hold the definition of a field in a set of flat test data records.
 * Multiple static factory methods exist for creating various pre-canned types
 * of test data field, e.g. a random IP address
 */
public class Field {

    //TODO add a builder for the Field class
    //TODO add an optional percentage of empty values, e.g. 10% are empty.

    private static final Logger LOGGER = LoggerFactory.getLogger(Field.class);

    private final String name;
    private final BiFunction<Random, Faker, String> valueFunction;

    /**
     * @param name          The name of the field
     * @param valueSupplier A supplier of values for the field
     */
    public Field(final String name,
                 final Supplier<String> valueSupplier) {

        this.name = Objects.requireNonNull(name);
        Objects.requireNonNull(valueSupplier);
        this.valueFunction = (ignoredRandom, ignoredFaker) -> valueSupplier.get();
    }

    /**
     * @param name          The name of the field
     * @param valueSupplier A supplier of values for the field
     */
    public Field(final String name,
                 final BiFunction<Random, Faker, String> valueSupplier) {

        this.name = Objects.requireNonNull(name);
        this.valueFunction = Objects.requireNonNull(valueSupplier);
    }

    /**
     * @return The next value for this field from the value supplier.
     * The value supplier may either be stateful, i.e. the next value is
     * dependant on values that cam before it or stateless, i.e. the next
     * value has no relation to previous values.
     */
    public String getNext(final Random random, final Faker faker) {
        return valueFunction.apply(random, faker);
    }

//    /**
//     * @return The next value for this field from the value supplier.
//     * The value supplier may either be stateful, i.e. the next value is
//     * dependant on values that cam before it or stateless, i.e. the next
//     * value has no relation to previous values.
//     */
//    public String getNext() {
//        return valueFunction.apply(null, null);
//    }

    /**
     * @return The name of the field
     */
    public String getName() {
        return name;
    }

}
