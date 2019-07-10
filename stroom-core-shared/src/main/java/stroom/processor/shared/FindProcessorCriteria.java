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

package stroom.processor.shared;

import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.BaseCriteria;

/**
 * Class used to find translations.
 */
public class FindProcessorCriteria extends BaseCriteria {
    private static final long serialVersionUID = 1L;

    private ExpressionOperator expression;

    public FindProcessorCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindProcessorCriteria(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }
}
