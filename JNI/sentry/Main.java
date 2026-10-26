import io.sentry.Sentry;

public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> exception.printStackTrace(System.err));
        Sentry.init(options -> {
            options.setDsn(System.getenv("SENTRY_DSN"));
            options.setEnvironment("local");
            options.setRelease("jni-lab1@1.0");
        });

        if (args.length > 0 && args[0].equals("sentry")) {
            try {
                throw new IllegalStateException("Test error from JNI lab");
            } catch (Exception exception) {
                System.out.println(
                        "Sentry event ID: " + Sentry.captureException(exception));
            } finally {
                Sentry.close();
            }

            return;
        }
        if (args.length > 0 && args[0].equals("sentry-jni")) {
            try {
                NativeLab.stringLength(null);
            } catch (NullPointerException exception) {
                System.out.println(
                        "Sentry event ID: " + Sentry.captureException(exception));
            } finally {
                Sentry.close();
            }

            return;
        }
        if (args.length > 0 && args[0].equals("sentry-unhandled")) {
            NativeLab.stringLength(null);
            return;
        }
        if (args.length > 0 && args[0].equals("sentry-native")) {
            int result = NativeLab.initNativeSentry();

            if (result != 0) {
                throw new IllegalStateException(
                        "Sentry Native initialization failed: " + result);
            }

            System.err.println("Sentry Native initialized; triggering crash");
            NativeLab.crashInCallChain();
            return;
        }
    }
}
