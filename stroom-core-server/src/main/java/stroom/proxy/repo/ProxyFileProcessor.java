package stroom.proxy.repo;

import java.nio.file.Path;
import java.util.List;

public interface FeedFileProcessor {
    void processFeedFiles(StroomZipRepository stroomZipRepository, String feed, List<Path> fileList);
}
