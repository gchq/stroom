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

package stroom.pipeline.xslt;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.XsltResource;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

class XsltResourceImpl implements XsltResource, RestResource, HasHealthCheck {
    private final XsltStore xsltStore;
    private final DocumentResourceHelper documentResourceHelper;

    @Inject
    XsltResourceImpl(final XsltStore xsltStore,
                     final DocumentResourceHelper documentResourceHelper) {
        this.xsltStore = xsltStore;
        this.documentResourceHelper = documentResourceHelper;
    }

    @Override
    public XsltDoc read(final DocRef docRef) {
        return documentResourceHelper.read(xsltStore, docRef);
    }

    @Override
    public XsltDoc update(final XsltDoc doc) {
        return documentResourceHelper.update(xsltStore, doc);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}