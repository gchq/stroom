package stroom.util.scheduler;

public class QuartzCronUtil {
    private QuartzCronUtil() {
        // Util
    }

    public static String convertLegacy(final String expression) {
        if (expression == null) {
            return null;
        }
        String converted = expression;
        final String[] parts = expression.split(" ");
        if (parts.length < 3) {
            throw new RuntimeException("CronExpression '" + expression + "' is invalid.");
        } else if (parts.length == 3) {
            converted = "0 " + expression + " * ?";
        }
        return converted;
    }
}
