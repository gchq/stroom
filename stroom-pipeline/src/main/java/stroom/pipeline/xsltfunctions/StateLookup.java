package stroom.pipeline.xsltfunctions;

import stroom.docref.DocRef;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;

public interface StateLookup {

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a store doc ref, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param docRef           A reference to the state doc.
     * @param lookupIdentifier The identifier to lookup in the reference data
     * @param result           The reference result object containing the proxy object for performing the lookup
     */
    void lookup(DocRef docRef,
                LookupIdentifier lookupIdentifier,
                ReferenceDataResult result);
}
