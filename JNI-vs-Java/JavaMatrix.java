import java.util.Arrays;
import java.util.Random;

public class JavaMatrix {
    final int rows;
    final int cols;
    // Элемент (i, j) хранится в data[i * cols + j]
    final double[] data;

    JavaMatrix(int rows, int cols) {
        this(rows, cols, new double[rows * cols]);
    }

    JavaMatrix(int rows, int cols, double[] data) {
        if (data.length != rows * cols) {
            throw new IllegalArgumentException("data.length != rows * cols");
        }

        this.rows = rows;
        this.cols = cols;
        this.data = data;
    }

    void fillRandom(long seed) {
        Random random = new Random(seed);

        for (int i = 0; i < data.length; i++) {
            data[i] = random.nextDouble();
        }
    }

    private static void checkSizes(JavaMatrix a, JavaMatrix b, JavaMatrix c) {
        if (a.cols != b.rows || c.rows != a.rows || c.cols != b.cols) {
            throw new IllegalArgumentException("Несогласованные размеры матриц");
        }
    }

    // Порядок ijk
    static void multiply(JavaMatrix a, JavaMatrix b, JavaMatrix c) {
        checkSizes(a, b, c);

        int n = a.rows;
        int m = a.cols;
        int p = b.cols;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                double sum = 0;

                for (int k = 0; k < m; k++) {
                    sum += a.data[i * m + k] * b.data[k * p + j];
                }

                c.data[i * p + j] = sum;
            }
        }
    }

    // Порядок ikj
    static void multiplyIkj(JavaMatrix a, JavaMatrix b, JavaMatrix c) {
        checkSizes(a, b, c);

        int n = a.rows;
        int m = a.cols;
        int p = b.cols;

        Arrays.fill(c.data, 0);

        for (int i = 0; i < n; i++) {
            for (int k = 0; k < m; k++) {
                double aik = a.data[i * m + k];

                for (int j = 0; j < p; j++) {
                    c.data[i * p + j] += aik * b.data[k * p + j];
                }
            }
        }
    }
}
