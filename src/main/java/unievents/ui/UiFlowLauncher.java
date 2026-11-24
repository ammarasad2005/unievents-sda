package unievents.ui;

/**
 * Plain (non-Application) launcher for {@link UiFlowTest} so the JVM does not
 * apply the JavaFX-runtime main-class check before our classpath is used.
 */
public final class UiFlowLauncher {

    private UiFlowLauncher() {
    }

    public static void main(String[] args) {
        UiFlowTest.main(args);
    }
}
