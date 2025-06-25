package stroom.data.zip;

class StroomZipNameException extends RuntimeException {

    private StroomZipNameException(final String msg) {
        super(msg);
    }

    static StroomZipNameException createDuplicateFileNameException(final String fileName) {
        return new StroomZipNameException("Duplicate File " + fileName);
    }

    static StroomZipNameException createOutOfOrderException(final String fileName) {
        return new StroomZipNameException("File Name is out of order " + fileName);
    }
}
