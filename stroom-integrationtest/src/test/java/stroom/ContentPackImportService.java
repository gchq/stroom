package stroom;

import org.springframework.stereotype.Component;
import stroom.importexport.server.ImportExportService;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;

@Component
public class ContentPackImportService {

   public static final String CONTENT_PACK_IMPORT_DIR = "transientContentPacks";

   private ImportExportService importExportService;

   @Inject
   public ContentPackImportService(final ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   public void importContentPacks() {

      Path contentPackPath =  new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), CONTENT_PACK_IMPORT_DIR).toPath();

      ContentPackDownloader.downloadContentPack("core-xml-schemas", Version.of(1,0), contentPackPath);
      ContentPackDownloader.downloadContentPack("event-logging-xml-schema", Version.of(1,0), contentPackPath);

      importExportService.performImportWithoutConfirmation(contentPackPath);
   }

}
