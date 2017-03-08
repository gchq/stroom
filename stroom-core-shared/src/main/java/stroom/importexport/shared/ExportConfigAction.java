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

package stroom.importexport.shared;

import stroom.entity.shared.Action;
import stroom.entity.shared.FindFolderCriteria;
import stroom.util.shared.ResourceGeneration;

public class ExportConfigAction extends Action<ResourceGeneration> {
    private static final long serialVersionUID = -3560107233301674555L;

    private FindFolderCriteria criteria;
    private boolean ignoreErrors;

    public ExportConfigAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExportConfigAction(final FindFolderCriteria criteria, final boolean ignoreErrors) {
        this.criteria = criteria;
        this.ignoreErrors = ignoreErrors;
    }

    public FindFolderCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(final FindFolderCriteria criteria) {
        this.criteria = criteria;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public void setIgnoreErrors(final boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    @Override
    public String getTaskName() {
        return "Export";
    }
}
