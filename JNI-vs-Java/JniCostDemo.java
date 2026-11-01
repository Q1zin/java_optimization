import java.util.Arrays;

public class JniCostDemo {
    private static final int EMPTY_CALLS = 50_000_000;

    private static double emptyCallNanos(int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            MatrixMultiplication.health();
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    private static double javaNanos(JavaMatrix x, JavaMatrix y, JavaMatrix z, int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            JavaMatrix.multiplyIkj(x, y, z);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    private static double nativeNanos(long x, long y, long z, int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            MatrixMultiplication.multiplyIkj(x, y, z);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    public static void main(String[] args) {
        int[] sizes = args.length > 0
                ? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
                : new int[] { 64, 32, 16, 8, 4, 2, 1 };

        emptyCallNanos(EMPTY_CALLS);
        double emptyCall = emptyCallNanos(EMPTY_CALLS);

        System.out.printf("Пустой JNI-вызов: %.1f ns%n%n", emptyCall);
        System.out.printf("%5s | %10s | %10s | %8s | %s%n", "N", "Java, ns", "C, ns", "доля JNI", "t Java / t C");

        for (int n : sizes) {
            JavaMatrix x = new JavaMatrix(n, n);
            JavaMatrix y = new JavaMatrix(n, n);
            JavaMatrix z = new JavaMatrix(n, n);

            x.fillRandom(1);
            y.fillRandom(2);

            long nativeX = MatrixMultiplication.fromArray(n, n, x.data);
            long nativeY = MatrixMultiplication.fromArray(n, n, y.data);
            long nativeZ = MatrixMultiplication.create(n, n);

            // Чем меньше матрица, тем больше вызовов, чтобы каждый замер длился заметное время
            int calls = Math.max(1000, 50_000_000 / (n * n * n));

            javaNanos(x, y, z, calls);
            double java = javaNanos(x, y, z, calls);

            nativeNanos(nativeX, nativeY, nativeZ, calls);
            double c = nativeNanos(nativeX, nativeY, nativeZ, calls);

            System.out.printf("%5d | %10.1f | %10.1f | %7.0f%% | %.2f%n",
                    n, java, c, 100 * emptyCall / c, java / c);

            MatrixMultiplication.free(nativeX);
            MatrixMultiplication.free(nativeY);
            MatrixMultiplication.free(nativeZ);
        }
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
