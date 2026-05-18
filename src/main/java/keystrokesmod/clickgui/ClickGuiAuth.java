package keystrokesmod.clickgui;

/**
 * Session gate for the click GUI. Users must enter the access code once per GUI open.
 */
public final class ClickGuiAuth {
    private static final String ACCESS_CODE = "2026key";

    private static boolean authenticated;

    private ClickGuiAuth() {
    }

    public static void beginSession() {
        authenticated = false;
    }

    public static void endSession() {
        authenticated = false;
    }

    public static boolean isAuthenticated() {
        return authenticated;
    }

    public static boolean tryAuthenticate(String input) {
        if (input != null && input.equals(ACCESS_CODE)) {
            authenticated = true;
            return true;
        }
        return false;
    }
}
