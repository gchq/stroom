package stroom.proxy.repo;

class StroomZipNameException extends RuntimeException {
    private static final long serialVersionUID = 6550229574319866082L;

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
