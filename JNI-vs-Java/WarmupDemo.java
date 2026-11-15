public class WarmupDemo {
    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        JavaMatrix x = new JavaMatrix(n, n);
        JavaMatrix y = new JavaMatrix(n, n);
        JavaMatrix z = new JavaMatrix(n, n);

        x.fillRandom(1);
        y.fillRandom(2);

        long nativeX = MatrixMultiplication.fromArray(n, n, x.data);
        long nativeY = MatrixMultiplication.fromArray(n, n, y.data);
        long nativeZ = MatrixMultiplication.create(n, n);

        // Без прогрева: печатаем каждый запуск, чтобы видеть, как Java ускоряется по мере работы JIT
        for (int run = 1; run <= runs; run++) {
            long start = System.nanoTime();
            JavaMatrix.multiply(x, y, z);
            long javaTime = System.nanoTime() - start;

            start = System.nanoTime();
            MatrixMultiplication.multiply(nativeX, nativeY, nativeZ);
            long nativeTime = System.nanoTime() - start;

            // Строка целиком за раз: с -XX:+PrintCompilation JIT пишет в тот же вывод из другого потока
            System.out.println(String.format("run %3d: Java %8.3f ms | C %8.3f ms",
                    run, javaTime / 1e6, nativeTime / 1e6));
        }

        MatrixMultiplication.free(nativeX);
        MatrixMultiplication.free(nativeY);
        MatrixMultiplication.free(nativeZ);
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
