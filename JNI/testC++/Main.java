public class Main {
    public static void main(String[] args) {
        TestJNI.printf("Hello from JNI!\n");
    }

    static {
        System.loadLibrary("TestJNINative");
    }
}
