import java.util.Arrays;

public class Main {
    private static final double[] EXPECTED_2X2 = { 19, 22, 43, 50 };

    private static double[] runJava(JavaMatrix x, JavaMatrix y) {
        // [[1, 2], [3, 4]] · [[5, 6], [7, 8]] = [[19, 22], [43, 50]]
        JavaMatrix a = new JavaMatrix(2, 2, new double[] { 1, 2, 3, 4 });
        JavaMatrix b = new JavaMatrix(2, 2, new double[] { 5, 6, 7, 8 });
        JavaMatrix c = new JavaMatrix(2, 2);

        JavaMatrix.multiply(a, b, c);

        System.out.println("Java 2x2: " + Arrays.toString(c.data)
                + (Arrays.equals(c.data, EXPECTED_2X2) ? " OK" : " FAIL"));

        JavaMatrix z = new JavaMatrix(x.rows, y.cols);

        for (int run = 1; run <= 2; run++) {
            long start = System.nanoTime();
            JavaMatrix.multiply(x, y, z);
            long elapsed = System.nanoTime() - start;

            System.out.printf("Java N=%d, run %2d: %8.2f ms%n", x.rows, run, elapsed / 1e6);
        }

        System.out.println("Java checksum: " + Arrays.stream(z.data).sum());

        return z.data;
    }

    private static double[] runNative(JavaMatrix x, JavaMatrix y) {
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

        long nativeX = MatrixMultiplication.fromArray(x.rows, x.cols, x.data);
        long nativeY = MatrixMultiplication.fromArray(y.rows, y.cols, y.data);
        long nativeZ = MatrixMultiplication.create(x.rows, y.cols);

        for (int run = 1; run <= 2; run++) {
            long start = System.nanoTime();
            MatrixMultiplication.multiply(nativeX, nativeY, nativeZ);
            long elapsed = System.nanoTime() - start;

            System.out.printf("C N=%d, run %2d: %8.2f ms%n", x.rows, run, elapsed / 1e6);
        }

        double[] product = MatrixMultiplication.toArray(nativeZ);

        System.out.println("C checksum: " + Arrays.stream(product).sum());

        MatrixMultiplication.free(nativeX);
        MatrixMultiplication.free(nativeY);
        MatrixMultiplication.free(nativeZ);

        return product;
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

    public static void main(String[] args) {
        boolean health = MatrixMultiplication.health();

        System.out.println("Health: " + health);

        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;

        JavaMatrix x = new JavaMatrix(n, n);
        JavaMatrix y = new JavaMatrix(n, n);

        x.fillRandom(1);
        y.fillRandom(2);

        double[] javaResult = runJava(x, y);
        double[] nativeResult = runNative(x, y);

        compare(javaResult, nativeResult);
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
