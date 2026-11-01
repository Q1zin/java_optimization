import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        boolean health = MatrixMultiplication.health();

        System.out.println("Health: " + health);

        // [[1, 2], [3, 4]] · [[5, 6], [7, 8]] = [[19, 22], [43, 50]]
        JavaMatrix a = new JavaMatrix(2, 2, new double[] { 1, 2, 3, 4 });
        JavaMatrix b = new JavaMatrix(2, 2, new double[] { 5, 6, 7, 8 });
        JavaMatrix c = new JavaMatrix(2, 2);

        JavaMatrix.multiply(a, b, c);

        System.out.println("2x2: " + Arrays.toString(c.data)
                + (Arrays.equals(c.data, new double[] { 19, 22, 43, 50 }) ? " OK" : " FAIL"));

        int n = args.length > 0 ? Integer.parseInt(args[0]) : 512;

        JavaMatrix x = new JavaMatrix(n, n);
        JavaMatrix y = new JavaMatrix(n, n);
        JavaMatrix z = new JavaMatrix(n, n);

        x.fillRandom(1);
        y.fillRandom(2);

        for (int run = 1; run <= 10; run++) {
            long start = System.nanoTime();
            JavaMatrix.multiply(x, y, z);
            long elapsed = System.nanoTime() - start;

            System.out.printf("N=%d, run %2d: %8.2f ms%n", n, run, elapsed / 1e6);
        }

        System.out.println("checksum: " + Arrays.stream(z.data).sum());
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
