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

package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.db.migration._V07_00_00.query.api.v2._V07_00_00_ExpressionOperator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"dataSource", "expression", "automate"})
@XmlRootElement(name = "query")
@XmlType(name = "QueryComponentSettings", propOrder = {"dataSource", "expression", "automate"})
public class _V07_00_00_QueryComponentSettings extends _V07_00_00_ComponentSettings {
    private static final long serialVersionUID = -2530827581046882396L;

    @XmlElement(name = "dataSource")
    private _V07_00_00_DocRef dataSource;
    @XmlElement(name = "expression")
    private _V07_00_00_ExpressionOperator expression;
    @XmlElement(name = "automate")
    private _V07_00_00_Automate automate;

    public _V07_00_00_QueryComponentSettings() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final _V07_00_00_DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public _V07_00_00_ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final _V07_00_00_ExpressionOperator expression) {
        this.expression = expression;
    }

    public _V07_00_00_Automate getAutomate() {
        return automate;
    }

    public void setAutomate(final _V07_00_00_Automate automate) {
        this.automate = automate;
    }
}
