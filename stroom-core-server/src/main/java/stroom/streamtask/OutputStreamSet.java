package stroom.streamtask;

import stroom.data.store.api.OutputStreamProvider;
import stroom.proxy.repo.StroomZipFileType;
import stroom.streamstore.shared.StreamTypeNames;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamSet implements Closeable {
    private final OutputStreamProvider outputStreamProvider;

    OutputStreamSet(final OutputStreamProvider outputStreamProvider) {
        this.outputStreamProvider = outputStreamProvider;
    }

    OutputStream next(final StroomZipFileType type) {
        final String dataType = convertType(type);
        return outputStreamProvider.next(dataType);
    }

    @Override
    public void close() throws IOException {
        this.outputStreamProvider.close();
    }

    private String convertType(StroomZipFileType type) {
        if (type == null || StroomZipFileType.Data.equals(type)) {
            return null;
        }
        switch (type) {
            case Meta:
                return StreamTypeNames.META;
            case Context:
                return StreamTypeNames.CONTEXT;
        }
        return null;
    }
}
