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

package stroom.xmlschema.server;

import org.springframework.stereotype.Component;
import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

import javax.inject.Inject;
import javax.inject.Named;

@ProvidesExplorerData
@Component
public class XMLSchemaExplorerDataProvider extends AbstractExplorerDataProvider<XMLSchema, FindXMLSchemaCriteria> {
    private final XMLSchemaService xmlSchemaService;

    @Inject
    XMLSchemaExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final XMLSchemaService xmlSchemaService) {
        super(cachedFolderService);
        this.xmlSchemaService = xmlSchemaService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(xmlSchemaService, treeModel);
    }

    @Override
    public String getType() {
        return XMLSchema.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return "XML Schema";
    }

    @Override
    public int getPriority() {
        return 13;
    }
}
