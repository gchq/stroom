package stroom.pipeline.reader;

interface XmlChars {

    /**
     * Returns true if the specified character is valid. This method
     * also checks the surrogate character range from 0x10000 to 0x10FFFF.
     *
     * @param c The character to check.
     */
    boolean isValid(int c);

    /**
     * Returns true if the specified character is valid and not a control
     * character. See https://www.w3.org/TR/xml11/#NT-RestrictedChar
     * This method also checks the surrogate character range from 0x10000 to 0x10FFFF.
     *
     * @param c The character to check.
     */
    boolean isValidLiteral(int c);
}
