package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportExportActionHandler {
    /**
     *
     * @param docRef
     * @param dataMap
     * @param importState
     * @param importMode
     * @return a tuple containing the imported DocRef and a String location where it is imported to
     */
    ImpexDetails importDocument(DocRef docRef, Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode);

    Map<String, byte[]> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    Set<DocRef> listDocuments();

    Map<DocRef, Set<DocRef>> getDependencies();

    String getType();

    Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef);

    ///////////////////////////////////////////////
    //End of ImportExportActionHandler interface //
    ///////////////////////////////////////////////


    /**
     * Class used to represent the result of operations of ImportExportActionHandler
     */
    class ImpexDetails {
        private String locationRef;
        private DocRef docRef;
        private boolean ignore;

        public ImpexDetails(){}

        public ImpexDetails(DocRef docRef){
            this.docRef = docRef;
        }

        public ImpexDetails(final DocRef docRef, final String locationRef){
            this.docRef = docRef;
            this.locationRef = locationRef;
        }

        public ImpexDetails(final DocRef docRef, final String locationRef, final boolean ignore){
            this.docRef = docRef;
            this.locationRef = locationRef;
            this.ignore = ignore;
        }

        public void setDocRef(DocRef docRef) {
            this.docRef = docRef;
        }

        public void setIgnore(boolean ignore) {
            this.ignore = ignore;
        }

        public void setLocationRef(String locationRef) {
            this.locationRef = locationRef;
        }

        public String getLocationRef() {
            return locationRef;
        }

        public DocRef getDocRef() {
            return docRef;
        }

        public boolean isIgnore () {
            return ignore;
        }
    }
}
