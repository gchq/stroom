package stroom;

import org.springframework.stereotype.Component;
import stroom.importexport.server.ImportExportService;
import stroom.test.StroomCoreServerTestFileUtil;
import stroom.util.shared.Version;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Component
public class ContentPackImportService {

   public static final String CONTENT_PACK_IMPORT_DIR = "transientContentPacks";

   private ImportExportService importExportService;

   @Inject
   public ContentPackImportService(final ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   public void importXmlSchemas() {
     importContentPacks(Arrays.asList(
             ContentPack.of("core-xml-schemas", Version.of(1,0)),
             ContentPack.of("event-logging-xml-schema", Version.of(1,0))
     ));
   }

   private void importContentPacks(final List<ContentPack> packs) {

      Path contentPackDirPath = getContentPackDirPath();

      packs.forEach(pack -> {
         Path packPath = ContentPackDownloader.downloadContentPack(
                 pack.getName(), pack.getVersion(), contentPackDirPath);
         importExportService.performImportWithoutConfirmation(packPath);
      });
   }

   private Path getContentPackDirPath() {
      return new File(StroomCoreServerTestFileUtil.getTestResourcesDir(), CONTENT_PACK_IMPORT_DIR).toPath();
   }

   private static class ContentPack {
      private final String name;
      private final Version version;

      public static ContentPack of(final String name, final Version version) {
         return new ContentPack(name, version);
      }

      public ContentPack(final String name, final Version version) {
         this.name = name;
         this.version = version;
      }

      public String getName() {
         return name;
      }

      public Version getVersion() {
         return version;
      }
   }

}
