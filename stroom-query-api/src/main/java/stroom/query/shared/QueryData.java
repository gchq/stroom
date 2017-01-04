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

package stroom.query.shared;

import stroom.entity.shared.DocRef;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "query", propOrder = {"dataSource", "expression", "limits", "automate"})
@XmlRootElement(name = "query")
public class QueryData extends ComponentSettings {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "dataSource")
    private DocRef dataSource;
    @XmlElement(name = "expression")
    private ExpressionOperator expression;
    @XmlElement(name = "limits")
    private Limits limits;
    @XmlElement(name = "automate")
    private Automate automate;

    public QueryData() {
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

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(final Limits limits) {
        this.limits = limits;
    }

    public Automate getAutomate() {
        return automate;
    }

    public void setAutomate(final Automate automate) {
        this.automate = automate;
    }
}
