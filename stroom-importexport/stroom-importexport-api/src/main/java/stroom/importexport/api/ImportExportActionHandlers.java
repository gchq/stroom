package stroom.importexport.api;

import java.util.Map;

/**
 * Provides an API into the ImportExportActionHandlers. The class is private
 * so this interface provides a public view into it.
 */
public interface ImportExportActionHandlers {

    /**
     * @return a Map of Type to Handler. Never returns null.
     */
    Map<String, ImportExportActionHandler> getHandlers();

}
