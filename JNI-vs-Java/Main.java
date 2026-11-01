import java.util.Arrays;

public class Main {
    private static final double[] EXPECTED_2X2 = { 19, 22, 43, 50 };

    private static void runJava(int n) {
        // [[1, 2], [3, 4]] · [[5, 6], [7, 8]] = [[19, 22], [43, 50]]
        JavaMatrix a = new JavaMatrix(2, 2, new double[] { 1, 2, 3, 4 });
        JavaMatrix b = new JavaMatrix(2, 2, new double[] { 5, 6, 7, 8 });
        JavaMatrix c = new JavaMatrix(2, 2);

        JavaMatrix.multiply(a, b, c);

        System.out.println("Java 2x2: " + Arrays.toString(c.data)
                + (Arrays.equals(c.data, EXPECTED_2X2) ? " OK" : " FAIL"));

        JavaMatrix x = new JavaMatrix(n, n);
        JavaMatrix y = new JavaMatrix(n, n);
        JavaMatrix z = new JavaMatrix(n, n);

        x.fillRandom(1);
        y.fillRandom(2);

        for (int run = 1; run <= 2; run++) {
            long start = System.nanoTime();
            JavaMatrix.multiply(x, y, z);
            long elapsed = System.nanoTime() - start;

            System.out.printf("Java N=%d, run %2d: %8.2f ms%n", n, run, elapsed / 1e6);
        }

        System.out.println("Java checksum: " + Arrays.stream(z.data).sum());
    }

    private static void runNative(int n) {
        long a = MatrixMultiplication.fromArray(2, 2, new double[] { 1, 2, 3, 4 });
        long b = MatrixMultiplication.fromArray(2, 2, new double[] { 5, 6, 7, 8 });
        long c = MatrixMultiplication.create(2, 2);

        MatrixMultiplication.multiply(a, b, c);

        double[] result = MatrixMultiplication.toArray(c);

        System.out.println("C 2x2: " + Arrays.toString(result)
                + (Arrays.equals(result, EXPECTED_2X2) ? " OK" : " FAIL"));

        MatrixMultiplication.free(a);
        MatrixMultiplication.free(b);
        MatrixMultiplication.free(c);

        long x = MatrixMultiplication.create(n, n);
        long y = MatrixMultiplication.create(n, n);
        long z = MatrixMultiplication.create(n, n);

        MatrixMultiplication.fillRandom(x, 1);
        MatrixMultiplication.fillRandom(y, 2);

        for (int run = 1; run <= 2; run++) {
            long start = System.nanoTime();
            MatrixMultiplication.multiply(x, y, z);
            long elapsed = System.nanoTime() - start;

            System.out.printf("C N=%d, run %2d: %8.2f ms%n", n, run, elapsed / 1e6);
        }

        System.out.println("C checksum: " + Arrays.stream(MatrixMultiplication.toArray(z)).sum());

        MatrixMultiplication.free(x);
        MatrixMultiplication.free(y);
        MatrixMultiplication.free(z);
    }

    public static void main(String[] args) {
        boolean health = MatrixMultiplication.health();

        System.out.println("Health: " + health);

        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;

        runJava(n);
        runNative(n);
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
