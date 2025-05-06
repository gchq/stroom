package stroom.planb.shared;

import stroom.docref.HasDisplayValue;

public enum StateKeyType implements HasDisplayValue {
    // Treat all keys as bytes.
    BYTE("Byte (1 byte from +127 to -128)"),
    // Treat all keys as shorts.
    SHORT("Short (2 bytes from +32,767 to -32,768)"),
    // Treat all keys as integers.
    INT("Integer (4 bytes from +2,147,483,647 to -2,147,483,648)"),
    // Treat all keys as longs.
    LONG("Long (8 bytes from +9,223,372,036,854,775,807 to -9,223,372,036,854,775,808)"),
    // Treat all keys as floats.
    FLOAT("Float (4 bytes from 3.402,823,5 E+38 to 1.4 E-45)"),
    // Treat all keys as doubles.
    DOUBLE("Double (8 bytes from 1.797,693,134,862,315,7 E+308 to 4.9 E-324)"),
    // Treat all keys as bytes but with a max length of 511.
    STRING("String (max 511 bytes)"),
    // Treat all keys as bytes but hashes and sequence numbers to allow for byte arrays longer than 511.
    LONG_STRING("Long string (unlimited bytes)"),
    // Always use a lookup table to store all keys. The key is a hash plus a sequence number.
    // Lookups deduplicate data and reduce storage requirements but impact performance.
    LOOKUP("Lookup table (unlimited bytes, deduplicated data)"),
    // Use automatic key type selection. The best key type is chosen to optimise performance and storage cost.
    AUTO("Automatic");

    private final String displayValue;

    StateKeyType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
