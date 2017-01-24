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

import java.text.ParseException;

public abstract class AbstractFunction implements Function, Appendable {
    final String name;
    final int minParams;
    final int maxParams;

    Object[] params;

    public AbstractFunction(final String name, final int minParams, final int maxParams) {
        this.name = name;
        this.minParams = minParams;
        this.maxParams = maxParams;
    }

    @Override
    public void setParams(final Object[] params) throws ParseException {
        if (params.length < minParams || params.length > maxParams) {
            throw new ExpressionException("Invalid number of parameters supplied for '" + name + "' + function");
        }

        this.params = params;
    }

    @Override
    public abstract Generator createGenerator();

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        appendString(sb);
        return sb.toString();
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(name);
        sb.append("(");
        appendParams(sb);
        sb.append(")");
    }

    protected void appendParams(final StringBuilder sb) {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                final Object param = params[i];
                appendParam(sb, param);
                if (i < params.length - 1) {
                    sb.append(", ");
                }
            }
        }
    }

    protected void appendParam(final StringBuilder sb, final Object param) {
        if (param instanceof Appendable) {
            ((Appendable) param).appendString(sb);
        } else if (param instanceof Double) {
            sb.append(TypeConverter.getString(param));
        } else {
            sb.append(TypeConverter.escape(param.toString()));
        }
    }

    @Override
    public boolean isAggregate() {
        return false;
    }
}
