package stroom.streamtask.server;

import stroom.proxy.repo.StroomZipRepository;

public interface FileSetProcessor {
    void process(StroomZipRepository stroomZipRepository, FileSet fileSet);
}
