package stroom.importexport.client.presenter;

import com.google.gwt.core.client.GWT;

public class ImportUtil {

    private ImportUtil() {
        // Utility class.
    }

    public static String getImportFileURL() {
        return GWT.getHostPageBaseURL() + "importfile.rpc";
    }
}
