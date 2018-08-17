package stroom.importexport;

public interface ImportExportActionHandlerFactory {
    ImportExportActionHandler create(String type);
}
