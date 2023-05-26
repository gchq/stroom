package stroom.data.zip;

class StroomZipNameException extends RuntimeException {

    private StroomZipNameException(String msg) {
        super(msg);
    }

    static StroomZipNameException createDuplicateFileNameException(String fileName) {
        return new StroomZipNameException("Duplicate File " + fileName);
    }

    static StroomZipNameException createOutOfOrderException(String fileName) {
        return new StroomZipNameException("File Name is out of order " + fileName);
    }
}
