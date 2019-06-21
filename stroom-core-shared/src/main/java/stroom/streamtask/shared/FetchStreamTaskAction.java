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

package stroom.streamtask.shared;

import stroom.entity.shared.Action;
import stroom.entity.shared.ResultList;
import stroom.query.api.v2.ExpressionOperator;

public class FetchStreamTaskAction extends Action<ResultList<StreamTask>> {
    private static final long serialVersionUID = -1773544031158236156L;

    private ExpressionOperator expression;

    public FetchStreamTaskAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchStreamTaskAction(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @Override
    public String getTaskName() {
        return "FetchStreamTaskAction - fetch()";
    }
}
