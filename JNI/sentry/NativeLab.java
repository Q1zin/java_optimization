public class NativeLab {
    static {
        System.loadLibrary("nativelab");
    }

    public static native int stringLength(String text);

    public static native void crashInCallChain();

    public static native int initNativeSentry();
}
