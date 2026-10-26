public class NativeLab {
    static {
        System.loadLibrary("nativelab");
    }

    public static native long allocateKilobyte();

    public static native void freeMemory(long address);

    public static native void consumeMemory();

    public static native void crashInCallChain();

    public static native void pressureMemory();
}