package stroom.streamtask.server;

import stroom.proxy.repo.StroomZipRepository;

public interface FilePackProcessor {
    void process(StroomZipRepository stroomZipRepository, FilePack filePack);
}
