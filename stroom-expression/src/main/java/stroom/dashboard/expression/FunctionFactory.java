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

package stroom.dashboard.expression;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class FunctionFactory {
    private final Map<String, Class<? extends Function>> map = new HashMap<String, Class<? extends Function>>();

    public FunctionFactory() {
        // Aggregate functions.
        add(Max.class, Max.NAME);
        add(Min.class, Min.NAME);
        add(Sum.class, Sum.NAME);
        add(Average.class, Average.NAME, Average.ALIAS);

        add(Round.class, Round.NAME);
        add(RoundYear.class, RoundYear.NAME);
        add(RoundMonth.class, RoundMonth.NAME);
        add(RoundDay.class, RoundDay.NAME);
        add(RoundHour.class, RoundHour.NAME);
        add(RoundMinute.class, RoundMinute.NAME);
        add(RoundSecond.class, RoundSecond.NAME);

        add(Ceiling.class, Ceiling.NAME);
        add(CeilingYear.class, CeilingYear.NAME);
        add(CeilingMonth.class, CeilingMonth.NAME);
        add(CeilingDay.class, CeilingDay.NAME);
        add(CeilingHour.class, CeilingHour.NAME);
        add(CeilingMinute.class, CeilingMinute.NAME);
        add(CeilingSecond.class, CeilingSecond.NAME);

        add(Floor.class, Floor.NAME);
        add(FloorYear.class, FloorYear.NAME);
        add(FloorMonth.class, FloorMonth.NAME);
        add(FloorDay.class, FloorDay.NAME);
        add(FloorHour.class, FloorHour.NAME);
        add(FloorMinute.class, FloorMinute.NAME);
        add(FloorSecond.class, FloorSecond.NAME);

        add(Replace.class, Replace.NAME);
        add(Concat.class, Concat.NAME);

		add(StringLength.class, StringLength.NAME);
		add(UpperCase.class, UpperCase.NAME);
		add(LowerCase.class, LowerCase.NAME);
		add(Substring.class, Substring.NAME);
		add(Decode.class, Decode.NAME);

		add(Count.class, Count.NAME);
		add(CountGroups.class, CountGroups.NAME);

        add(Power.class, Power.NAME, Power.ALIAS);
        add(Divide.class, Divide.NAME, Divide.ALIAS);
        add(Multiply.class, Multiply.NAME, Multiply.ALIAS);
        add(Add.class, Add.NAME, Add.ALIAS);
		add(Subtract.class, Subtract.NAME, Subtract.ALIAS);
        add(Negate.class, Negate.NAME);
        add(Equals.class, Equals.NAME, Equals.ALIAS);
        add(GreaterThan.class, GreaterThan.NAME, GreaterThan.ALIAS);
        add(LessThan.class, LessThan.NAME, LessThan.ALIAS);
		add(GreaterThanOrEqualTo.class, GreaterThanOrEqualTo.NAME, GreaterThanOrEqualTo.ALIAS);
		add(LessThanOrEqualTo.class, LessThanOrEqualTo.NAME, LessThanOrEqualTo.ALIAS);

        add(Random.class, Random.NAME);
    }

    private void add(final Class<? extends Function> clazz, final String... names) {
        for (final String name : names) {
            map.put(name.toLowerCase(), clazz);
        }
    }

    public Function create(final String functionName) {
        final Class<? extends Function> clazz = map.get(functionName.toLowerCase());
        if (clazz != null) {
            try {
                return clazz.getConstructor(String.class).newInstance(functionName);
            } catch (final NoSuchMethodException | InvocationTargetException | InstantiationException
                    | IllegalAccessException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        return null;
    }
}
