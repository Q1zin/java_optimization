public class VectorizeDemo {
    // Один индекс j у c и b: даже если c и b - один массив, итерации не зависят друг от друга
    static void sameIndex(double[] c, double[] b, double s, int n) {
        for (int j = 0; j < n; j++) {
            c[j] += s * b[j];
        }
    }

    // Как внутренний цикл multiplyIkj. Если c и b - один массив, а смещения разные,
    // запись в c может изменить то, что следующие итерации прочитают из b
    static void withOffsets(double[] c, int cOffset, double[] b, int bOffset, double s, int n) {
        for (int j = 0; j < n; j++) {
            c[cOffset + j] += s * b[bOffset + j];
        }
    }

    public static void main(String[] args) {
        int n = 256;
        int calls = 2_000_000;
        double[] c = new double[n * n];
        double[] b = new double[n * n];

        for (int round = 1; round <= 3; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                sameIndex(c, b, 0.5, n);
            }
            double same = (double) (System.nanoTime() - start) / calls / n;

            start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                int offset = (i % n) * n;
                withOffsets(c, offset, b, offset, 0.5, n);
            }
            double sameOffsets = (double) (System.nanoTime() - start) / calls / n;

            start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                withOffsets(c, (i % n) * n, b, (i * 7 % n) * n, 0.5, n);
            }
            double differentOffsets = (double) (System.nanoTime() - start) / calls / n;

            System.out.printf("round %d, ns на элемент: c[j] += s*b[j] %.3f | одинаковые смещения %.3f | разные смещения %.3f%n",
                    round, same, sameOffsets, differentOffsets);
        }
    }
}
