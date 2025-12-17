/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.event.logging.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerNode;

import event.logging.BaseObject;
import event.logging.Data;
import event.logging.OtherObject;
import event.logging.OtherObject.Builder;

import java.util.Iterator;

class BulkActionResultObjectInfoProvider  implements ObjectInfoProvider {

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final BulkActionResult bulkActionResult = (BulkActionResult) obj;

        final Builder<Void> builder =
                OtherObject.builder()
                .withType("BulkActionResult")
                .withDescription("Bulk Action Result" +
                        ((bulkActionResult.getMessage() != null) ? ": " + bulkActionResult.getMessage() : ""));
        if (bulkActionResult.getExplorerNodes() != null) {
            builder.withName("" + bulkActionResult.getExplorerNodes().size() + " docrefs; see data elements");

            final Iterator<ExplorerNode> itr = bulkActionResult.getExplorerNodes().iterator();
            final int index = 1;
            while (itr.hasNext()) {
                final ExplorerNode docRef = itr.next();
                builder.addData(Data.builder().withName("itemName" + index).withValue(docRef.getName()).build());
                builder.addData(Data.builder().withName("itemType" + index).withValue(docRef.getType()).build());
                builder.addData(Data.builder().withName("itemUuid" + index).withValue(docRef.getUuid()).build());
            }
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
