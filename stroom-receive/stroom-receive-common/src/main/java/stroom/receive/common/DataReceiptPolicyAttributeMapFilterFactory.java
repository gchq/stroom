package stroom.receive.common;

import stroom.docref.DocRef;

public interface DataReceiptPolicyAttributeMapFilterFactory {
    AttributeMapFilter create(DocRef policyRef);
}
