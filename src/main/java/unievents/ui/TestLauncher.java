package unievents.ui;

/**
 * Plain (non-Application) launcher so the java command does not detect an
 * Application class as the main class (which triggers the missing-runtime
 * check before our classpath is applied).
 */
public final class TestLauncher {

    public static void main(String[] args) {
        UiSmokeTest.main(args);
    }
}
