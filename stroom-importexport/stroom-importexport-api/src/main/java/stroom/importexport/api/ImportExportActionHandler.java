package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.HasDependencies;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportExportActionHandler extends HasDependencies {

    /**
     * @param docRef
     * @param dataMap
     * @param importState
     * @param importSettings
     * @return a tuple containing the imported DocRef and a String location where it is imported to
     */
    DocRef importDocument(DocRef docRef,
                          Map<String, byte[]> dataMap,
                          ImportState importState,
                          ImportSettings importSettings);

    Map<String, byte[]> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    Set<DocRef> listDocuments();

    String getType();

    Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef);

    /**
     * @return True if the type of document handled by this {@link ImportExportActionHandler}
     * is a singleton, i.e. there is only ever zero or one document of this type, never more than one.
     * <p>
     * Examples of a singleton docs would are
     * {@link stroom.data.retention.shared.DataRetentionRules}
     * or {@link stroom.receive.rules.shared.ReceiveDataRules}.
     * </p>
     */
    default boolean isSingleton() {
        return false;
    }

    /**
     * @return True if the current user has permission to export the document represented by
     * the supplied docRef.
     */
    boolean canExport(final DocRef docRef);

}
