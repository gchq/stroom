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

import java.io.Serializable;
import java.text.ParseException;

public class Substring extends AbstractFunction implements Serializable {
	private static class Gen extends AbstractSingleChildGenerator {
		private static final long serialVersionUID = 8153777070911899616L;
		
		private Double startPos;
		private Double endPos;

		public Gen(final Generator childGenerator, final Double startPos, final Double endPos) {
			super(childGenerator);
			this.startPos = startPos;
			this.endPos = endPos;
		}

		@Override
		public void set(final String[] values) {
			childGenerator.set(values);
		}

		@Override
		public Object eval() {
			final Object val = childGenerator.eval();
			if (val != null) {
				int len = endPos.intValue();
				if (endPos >= TypeConverter.getString(val).length()){
					len = TypeConverter.getString(val).length();
				}
				return TypeConverter.getString(val).substring(startPos.intValue(), len);
			}

			return null;
		}
	}

	private static final long serialVersionUID = -305845496003936297L;
	public static final String NAME = "substring";

	private Generator gen;
	private Function function = null;
	private boolean hasAggregate;
	
	private Double startPos;
	private Double endPos;

	public Substring(final String name) {
		super(name, 3, 3);
	}

	@Override
	public void setParams(final Object[] params) throws ParseException {
		super.setParams(params);

		if (!(params[1] instanceof Double)) {
			throw new ParseException("Number expected as second argument of '" + name + "' function", 0);
		}
		startPos = (Double)params[1];
		if (startPos < 0) {
			startPos = 0D;
		}
		if (!(params[2] instanceof Double)) {
			throw new ParseException("Number expected as third argument of '" + name + "' function", 0);
		}
		endPos = (Double)params[2];

		final Object param = params[0];
		if (param instanceof Function) {
			function = (Function) param;
			hasAggregate = function.hasAggregate();
		} else {
			/*
			 * Optimise replacement of static input in case user does something
			 * stupid.
			 */
			int len = endPos.intValue();
			if (endPos >= param.toString().length()){
				len = param.toString().length();
			}
			final String newValue = param.toString().substring(startPos.intValue(),len);
			gen = new StaticValueFunction(newValue).createGenerator();
			hasAggregate = false;
		}
	}

	@Override
	public Generator createGenerator() {
		if (gen != null) {
			return gen;
		}

		final Generator childGenerator = function.createGenerator();
		return new Gen(childGenerator, startPos, endPos);
	}

	@Override
	public boolean hasAggregate() {
		return hasAggregate;
	}
}
