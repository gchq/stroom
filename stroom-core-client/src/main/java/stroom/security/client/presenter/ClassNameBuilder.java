package stroom.security.client.presenter;

public class ClassNameBuilder {

    private String text = "";

    public void addClassName(final String className) {
        if (text.length() > 0) {
            text += " ";
        }
        text += className;
    }

    public String build() {
        if (text.length() == 0) {
            return text;
        }
        return " class=\"" + text + "\"";
    }
}
