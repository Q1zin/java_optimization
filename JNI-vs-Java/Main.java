public class Main {
    public static void main(String[] args) {
        boolean health = MatrixMultiplication.health();

        System.out.println("Health: " + health);
    }

    static {
        System.loadLibrary("MatrixMultiplication");
    }
}
