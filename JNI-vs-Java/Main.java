import java.util.Arrays;

public class Main {
    private static final double[] EXPECTED_2X2 = { 19, 22, 43, 50 };

    private static final int WARMUP_RUNS = 3;
    // JIT компилирует в фоне, и на маленьких N трёх запусков мало, поэтому греем ещё и по времени
    private static final long WARMUP_NANOS = 500_000_000L;
    private static final int MEASURED_RUNS = 10;

    private static void checkSmall() {
        // [[1, 2], [3, 4]] · [[5, 6], [7, 8]] = [[19, 22], [43, 50]]
        JavaMatrix a = new JavaMatrix(2, 2, new double[] { 1, 2, 3, 4 });
        JavaMatrix b = new JavaMatrix(2, 2, new double[] { 5, 6, 7, 8 });
        JavaMatrix c = new JavaMatrix(2, 2);

        JavaMatrix.multiply(a, b, c);

        System.out.println("Java 2x2: " + Arrays.toString(c.data)
                + (Arrays.equals(c.data, EXPECTED_2X2) ? " OK" : " FAIL"));

        long nativeA = MatrixMultiplication.fromArray(2, 2, a.data);
        long nativeB = MatrixMultiplication.fromArray(2, 2, b.data);
        long nativeC = MatrixMultiplication.create(2, 2);

        MatrixMultiplication.multiply(nativeA, nativeB, nativeC);

        double[] result = MatrixMultiplication.toArray(nativeC);

        System.out.println("C 2x2: " + Arrays.toString(result)
                + (Arrays.equals(result, EXPECTED_2X2) ? " OK" : " FAIL"));

        MatrixMultiplication.free(nativeA);
        MatrixMultiplication.free(nativeB);
        MatrixMultiplication.free(nativeC);
    }

    private static void compare(double[] javaResult, double[] nativeResult) {
        int different = 0;
        double maxDifference = 0;

        for (int i = 0; i < javaResult.length; i++) {
            if (javaResult[i] != nativeResult[i]) {
                different++;
                maxDifference = Math.max(maxDifference, Math.abs(javaResult[i] - nativeResult[i]));
            }
        }

        if (different != 0) {
            System.out.println("Java result: " + Arrays.toString(javaResult));
            System.out.println("C result:    " + Arrays.toString(nativeResult));
            System.out.println("Java vs C: различаются " + different + " из " + javaResult.length
                    + " элементов, макс. разница " + maxDifference);
        }
    }

    private static double mean(long[] times) {
        return Arrays.stream(times).average().getAsDouble();
    }

    // Насколько самый медленный замер дольше самого быстрого, в процентах от среднего
    private static double spread(long[] times) {
        long min = Arrays.stream(times).min().getAsLong();
        long max = Arrays.stream(times).max().getAsLong();

        return 100.0 * (max - min) / mean(times);
    }

    private static void benchmark(int n) {
        JavaMatrix x = new JavaMatrix(n, n);
        JavaMatrix y = new JavaMatrix(n, n);
        JavaMatrix z = new JavaMatrix(n, n);

        x.fillRandom(1);
        y.fillRandom(2);

        long nativeX = MatrixMultiplication.fromArray(n, n, x.data);
        long nativeY = MatrixMultiplication.fromArray(n, n, y.data);
        long nativeZ = MatrixMultiplication.create(n, n);

        long[] javaTimes = new long[MEASURED_RUNS];
        long[] nativeTimes = new long[MEASURED_RUNS];

        // Прогрев: пока JIT компилирует multiply, время не учитываем
        long warmupStart = System.nanoTime();

        for (int run = 0; run < WARMUP_RUNS || System.nanoTime() - warmupStart < WARMUP_NANOS; run++) {
            JavaMatrix.multiply(x, y, z);
            MatrixMultiplication.multiply(nativeX, nativeY, nativeZ);
        }

        // Java и C по очереди, чтобы нагрев процессора и фоновые задачи влияли на обоих одинаково
        for (int run = 0; run < MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            JavaMatrix.multiply(x, y, z);
            javaTimes[run] = System.nanoTime() - start;

            start = System.nanoTime();
            MatrixMultiplication.multiply(nativeX, nativeY, nativeZ);
            nativeTimes[run] = System.nanoTime() - start;
        }

        compare(z.data, MatrixMultiplication.toArray(nativeZ));

        MatrixMultiplication.free(nativeX);
        MatrixMultiplication.free(nativeY);
        MatrixMultiplication.free(nativeZ);

        double javaMs = mean(javaTimes) / 1e6;
        double nativeMs = mean(nativeTimes) / 1e6;
        // На каждом из N³ шагов одно умножение и одно сложение
        double flops = 2.0 * n * n * n;

        System.out.printf("%5d | %8.2f | %6.0f%% | %8.2f | %6.0f%% | %11.2f | %8.2f | %.2f%n",
                n, javaMs, spread(javaTimes), nativeMs, spread(nativeTimes),
                flops / javaMs / 1e6, flops / nativeMs / 1e6, javaMs / nativeMs);
    }

    public static void main(String[] args) {
        boolean health = MatrixMultiplication.health();

        System.out.println("Health: " + health);

        checkSmall();

        int[] sizes = args.length > 0
                ? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
                : new int[] { 64, 128, 256, 512, 1024 };

        System.out.printf("%n%5s | %8s | %7s | %8s | %7s | %11s | %8s | %s%n",
                "N", "Java, ms", "разброс", "C, ms", "разброс", "Java GFLOPS", "C GFLOPS", "t Java / t C");

        for (int n : sizes) {
            benchmark(n);
        }
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
