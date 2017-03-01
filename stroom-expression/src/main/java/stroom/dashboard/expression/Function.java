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

package stroom.dashboard.expression;

import java.text.ParseException;

public interface Function {
    /**
     * Set the parameters that this function will use.
     *
     * @param params The parameters that this function will use.
     * @throws ParseException If any parameters are illegal/unexpected then throw an
     *                        exception.
     */
    void setParams(Object[] params) throws ParseException;

    /**
     * Create a generator to generate a value for a cell based on the function
     * and supplied cell data.
     *
     * @return A generator.
     */
    Generator createGenerator();

    /**
     * Is this function operating as an aggregate, i.e. will it combine multiple
     * cell values when grouping is applied. Examples of aggregate functions are
     * sum(), min(), max(), average(), count().
     *
     * @return True if this is an aggregating function.
     */
    boolean isAggregate();

    /**
     * Is this function an aggregating function or are any of the child
     * parameters used going to aggregate data.
     *
     * @return True if this function is an aggregating function or any child
     * parameters used will aggregate data.
     */
    boolean hasAggregate();
}
