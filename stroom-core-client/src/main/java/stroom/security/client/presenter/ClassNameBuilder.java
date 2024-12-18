package stroom.security.client.presenter;

public class ClassNameBuilder {

    private String text = "";

    public void addClassName(final String className) {
        if (className != null && className.length() > 0) {
            if (text.length() > 0) {
                text += " ";
            }
            text += className;
        }
    }

    public String build() {
        return text;
    }

    public String buildClassAttribute() {
        if (text.length() == 0) {
            return text;
        }
        return " class=\"" + text + "\"";
    }
}
