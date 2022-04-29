package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;

public record RepoSourceEntry(StroomZipFileType type,
                              String extension,
                              long byteSize) {

}
