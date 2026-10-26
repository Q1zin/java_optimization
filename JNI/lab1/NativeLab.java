public class NativeLab {
    static {
        System.loadLibrary("nativelab");
    }

    public static native long allocateKilobyte();

    public static native void freeMemory(long address);

    public static native void consumeMemory();

    public static native void crashInCallChain();

    public static native void pressureMemory();

    public static native int stringLength(String text);

    public static native void callBeanMethod(Bean bean);

    public static native void changeBeanInt(Bean bean, int value);

    public static native long createNativeData(int value);

    public static native int readNativeData(long address);

    public static native void freeNativeData(long address);

    public static native int initNativeSentry();
}