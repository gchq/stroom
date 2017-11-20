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

package stroom.streamstore.shared;

import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.shared.OwnedBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;

@XmlType(name = "query", propOrder = {"dataSource", "expression", "limits"})
@XmlRootElement(name = "query")
public class QueryData implements Serializable {

    public static final String STREAM_STORE_TYPE = "StreamStore";
    public static final DocRef STREAM_STORE_DOC_REF = new DocRef.Builder()
            .type(STREAM_STORE_TYPE)
            .uuid("0")
            .name(STREAM_STORE_TYPE)
            .build();

    private static final long serialVersionUID = -2530827581046882396L;

    private DocRef dataSource;
    private ExpressionOperator expression;
    private Limits limits;

    public QueryData() {
        // Default constructor necessary for GWT serialisation.
    }

    @XmlElement
    public DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    @XmlElement
    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @XmlElement
    public Limits getLimits() {
        return limits;
    }

    public void setLimits(final Limits limits) {
        this.limits = limits;
    }

    public static abstract class ABuilder<
                    OwningBuilder extends OwnedBuilder,
                    CHILD_CLASS extends ABuilder<OwningBuilder, ?>>
            extends OwnedBuilder<OwningBuilder, QueryData, CHILD_CLASS> {

        private final QueryData instance;

        public ABuilder() {
            this.instance = new QueryData();
        }

        public ABuilder dataSource(final DocRef value) {
            this.instance.dataSource = value;
            return this;
        }

        public DocRef.OBuilder<CHILD_CLASS> dataSource() {
            return new DocRef.OBuilder<CHILD_CLASS>()
                    .popToWhenComplete(self(), this::dataSource);
        }

        public CHILD_CLASS limits(final Limits value) {
            this.instance.limits = value;
            return self();
        }

        public Limits.OBuilder<CHILD_CLASS> limits() {
            return new Limits.OBuilder<CHILD_CLASS>()
                    .popToWhenComplete(self(), this::limits);
        }

        public CHILD_CLASS expression(final ExpressionOperator value) {
            this.instance.expression = value;
            return self();
        }

        public ExpressionOperator.OBuilder<CHILD_CLASS> expression(ExpressionOperator.Op operator) {
            return new ExpressionOperator.OBuilder<CHILD_CLASS>(operator)
                    .popToWhenComplete(self(), this::expression);
        }

        @Override
        protected QueryData pojoBuild() {
            return instance;
        }
    }

    /**
     * A builder that is owned by another builder, used for popping back up a stack
     *
     * @param <OwningBuilder> The class of the parent builder
     */
    public static final class OBuilder<OwningBuilder extends OwnedBuilder>
            extends ABuilder<OwningBuilder, OBuilder<OwningBuilder>> {
        @Override
        public OBuilder<OwningBuilder> self() {
            return this;
        }
    }

    /**
     * A builder that is created independently of any parent builder
     */
    public static final class Builder extends ABuilder<Builder, Builder> {

        @Override
        public Builder self() {
            return this;
        }
    }
}
