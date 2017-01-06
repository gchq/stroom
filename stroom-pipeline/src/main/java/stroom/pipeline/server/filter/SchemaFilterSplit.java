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

package stroom.pipeline.server.filter;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.spring.StroomScope;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;

import javax.inject.Inject;
import javax.xml.XMLConstants;

/**
 * An XML filter for performing inline schema validation of XML.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "SchemaFilter", category = Category.FILTER, roles = { PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_STEPPING,
        PipelineElementType.ROLE_VALIDATOR }, icon = ElementIcons.XSD)
public class SchemaFilterSplit extends AbstractXMLFilter {
    private final SchemaFilter schemaFilter;
    private final FindXMLSchemaCriteria schemaConstraint = new FindXMLSchemaCriteria();
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private boolean schemaValidation = true;
    private boolean addedSchemaFilter;

    @Inject
    public SchemaFilterSplit(final SchemaFilter schemaFilter) {
        this.schemaFilter = schemaFilter;
    }

    private AbstractXMLFilter getSchemaFilter() {
        schemaFilter.setSchemaLanguage(schemaLanguage);
        schemaFilter.setSchemaValidation(schemaValidation);
        schemaFilter.setSchemaConstraint(schemaConstraint);

        // Wrap the schema filter in a split filter so that it errors on all top
        // level elements.
        final SplitFilter splitFilter = new SplitFilter();
        splitFilter.setSplitCount(1);
        splitFilter.setTarget(schemaFilter);
        return splitFilter;
    }

    @Override
    public void startProcessing() {
        try {
            if (!addedSchemaFilter) {
                addedSchemaFilter = true;
                addTarget(getSchemaFilter());
            }
        } finally {
            super.startProcessing();
        }
    }

    @Override
    public String getElementId() {
        return schemaFilter.getElementId();
    }

    @Override
    public void setElementId(final String id) {
        schemaFilter.setElementId(id);
    }

    @PipelineProperty(description = "The schema language that the schema is written in.", defaultValue = XMLConstants.W3C_XML_SCHEMA_NS_URI)
    public void setSchemaLanguage(final String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    @PipelineProperty(description = "Should schema validation be performed?", defaultValue = "true")
    public void setSchemaValidation(final boolean schemaValidation) {
        this.schemaValidation = schemaValidation;
    }

    @PipelineProperty(description = "Limits the schemas that can be used to validate data to those with a matching schema group name.")
    public void setSchemaGroup(final String schemaGroup) {
        if (schemaGroup != null && schemaGroup.trim().length() > 0) {
            schemaConstraint.setSchemaGroup(schemaGroup.trim());
        } else {
            schemaConstraint.setSchemaGroup(null);
        }
    }

    @PipelineProperty(description = "Limits the schemas that can be used to validate data to those with a matching namespace URI.")
    public void setNamespaceURI(final String namespaceURI) {
        if (namespaceURI != null && namespaceURI.trim().length() > 0) {
            schemaConstraint.setNamespaceURI(namespaceURI.trim());
        } else {
            schemaConstraint.setNamespaceURI(null);
        }
    }

    @PipelineProperty(description = "Limits the schemas that can be used to validate data to those with a matching system id.")
    public void setSystemId(final String systemId) {
        if (systemId != null && systemId.trim().length() > 0) {
            schemaConstraint.setSystemId(systemId.trim());
        } else {
            schemaConstraint.setSystemId(null);
        }
    }
}
