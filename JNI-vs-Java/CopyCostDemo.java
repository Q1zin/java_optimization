import java.util.Arrays;

public class CopyCostDemo {
    // Сюда сохраняем созданный массив, иначе JIT поймёт, что он не нужен, и уберёт выделение памяти
    private static double[] sink;

    private static double javaNanos(JavaMatrix x, JavaMatrix y, JavaMatrix z, int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            JavaMatrix.multiplyIkj(x, y, z);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    // Данные уже лежат в C
    private static double nativeNanos(long x, long y, long z, int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            MatrixMultiplication.multiplyIkj(x, y, z);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    // Данные живут в Java: на каждом вызове копируем A и B в C, умножаем и копируем результат обратно
    private static double nativeWithCopyNanos(JavaMatrix x, JavaMatrix y, int calls) {
        int n = x.rows;
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            long nativeX = MatrixMultiplication.fromArray(n, n, x.data);
            long nativeY = MatrixMultiplication.fromArray(n, n, y.data);
            long nativeZ = MatrixMultiplication.create(n, n);

            MatrixMultiplication.multiplyIkj(nativeX, nativeY, nativeZ);
            MatrixMultiplication.toArray(nativeZ);

            MatrixMultiplication.free(nativeX);
            MatrixMultiplication.free(nativeY);
            MatrixMultiplication.free(nativeZ);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    // Данные живут в Java: C умножает сами Java-массивы, без копирования
    private static double nativeWithoutCopyNanos(JavaMatrix x, JavaMatrix y, JavaMatrix z, int calls) {
        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            MatrixMultiplication.multiplyIkjArrays(x.data, y.data, z.data, x.rows);
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    // Runnable добавляет пару наносекунд на вызов, на фоне сотен наносекунд это не важно
    private static double nanos(Runnable call, int calls) {
        for (int i = 0; i < calls; i++) {
            call.run();
        }

        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            call.run();
        }

        return (double) (System.nanoTime() - start) / calls;
    }

    private static void printBreakdown() {
        int calls = 2_000_000;
        JavaMatrix x = new JavaMatrix(1, 1);
        long nativeZ = MatrixMultiplication.create(1, 1);

        x.fillRandom(1);

        System.out.printf("%nИз чего складывается копия при N=1:%n");
        System.out.printf("  create + free          %6.1f ns%n",
                nanos(() -> MatrixMultiplication.free(MatrixMultiplication.create(1, 1)), calls));
        System.out.printf("  fromArray + free       %6.1f ns%n",
                nanos(() -> MatrixMultiplication.free(MatrixMultiplication.fromArray(1, 1, x.data)), calls));
        System.out.printf("  toArray                %6.1f ns%n",
                nanos(() -> MatrixMultiplication.toArray(nativeZ), calls));
        System.out.printf("  new double[1] в Java   %6.1f ns%n",
                nanos(() -> sink = new double[1], calls));

        MatrixMultiplication.free(nativeZ);
    }

    public static void main(String[] args) {
        // От большого N к маленькому, как в JniCostDemo
        int[] sizes = args.length > 0
                ? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
                : new int[] { 512, 256, 128, 64, 32, 16, 8, 4, 2, 1 };

        System.out.printf("%5s | %12s | %12s | %12s | %15s | %18s | %s%n", "N", "Java, ns", "C, ns",
                "C+копия, ns", "C без копии, ns", "t Java / t C+копия", "t Java / t C без копии");

        for (int n : sizes) {
            JavaMatrix x = new JavaMatrix(n, n);
            JavaMatrix y = new JavaMatrix(n, n);
            JavaMatrix z = new JavaMatrix(n, n);
            JavaMatrix zWithoutCopy = new JavaMatrix(n, n);

            x.fillRandom(1);
            y.fillRandom(2);

            long nativeX = MatrixMultiplication.fromArray(n, n, x.data);
            long nativeY = MatrixMultiplication.fromArray(n, n, y.data);
            long nativeZ = MatrixMultiplication.create(n, n);

            int calls = (int) Math.max(10, 5_000_000L / ((long) n * n * n));

            javaNanos(x, y, z, calls);
            double java = javaNanos(x, y, z, calls);

            nativeNanos(nativeX, nativeY, nativeZ, calls);
            double c = nativeNanos(nativeX, nativeY, nativeZ, calls);

            nativeWithCopyNanos(x, y, calls);
            double cWithCopy = nativeWithCopyNanos(x, y, calls);

            nativeWithoutCopyNanos(x, y, zWithoutCopy, calls);
            double cWithoutCopy = nativeWithoutCopyNanos(x, y, zWithoutCopy, calls);

            System.out.printf("%5d | %12.1f | %12.1f | %12.1f | %15.1f | %18.2f | %.2f%n",
                    n, java, c, cWithCopy, cWithoutCopy, java / cWithCopy, java / cWithoutCopy);

            if (!Arrays.equals(z.data, zWithoutCopy.data)) {
                System.out.println("  FAIL: результат C без копии не совпадает с Java");
            }

            MatrixMultiplication.free(nativeX);
            MatrixMultiplication.free(nativeY);
            MatrixMultiplication.free(nativeZ);
        }

        printBreakdown();
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
