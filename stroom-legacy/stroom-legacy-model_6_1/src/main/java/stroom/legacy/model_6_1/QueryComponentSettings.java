/*
 * Copyright 2017 Crown Copyright
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

package stroom.legacy.model_6_1;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "query", propOrder = {"dataSource", "expression", "automate"})
@XmlRootElement(name = "query")
@Deprecated
public class QueryComponentSettings extends ComponentSettings {

    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "dataSource")
    private DocRef dataSource;
    @XmlElement(name = "expression")
    private ExpressionOperator expression;
    @XmlElement(name = "automate")
    private Automate automate;

    public QueryComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public Automate getAutomate() {
        return automate;
    }

    public void setAutomate(final Automate automate) {
        this.automate = automate;
    }
}
