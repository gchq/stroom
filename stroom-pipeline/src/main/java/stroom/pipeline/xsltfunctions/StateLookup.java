package stroom.pipeline.xsltfunctions;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.shared.data.PipelineReference;

import java.util.List;

public interface StateLookup {

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a list of ref data pipelines, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param pipelineReferences The references to look for reference data in.
     * @param lookupIdentifier   The identifier to lookup in the reference data
     * @param result             The reference result object containing the proxy object for performing the lookup
     * @return The passed result object
     */
    ReferenceDataResult ensureReferenceDataAvailability(List<PipelineReference> pipelineReferences,
                                                        LookupIdentifier lookupIdentifier,
                                                        ReferenceDataResult result);
}
