package stroom.event.logging.impl;

import stroom.docref.DocRef;
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

            Iterator<ExplorerNode> itr = bulkActionResult.getExplorerNodes().iterator();
            int index = 1;
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
