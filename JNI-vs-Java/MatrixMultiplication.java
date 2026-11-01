public class MatrixMultiplication {
    public static native boolean health();

    public static native long create(int rows, int cols);

    public static native long fromArray(int rows, int cols, double[] values);

    public static native double[] toArray(long matrix);

    public static native void multiply(long a, long b, long c);

    public static native void multiplyIkj(long a, long b, long c);

    public static native void free(long matrix);
}
