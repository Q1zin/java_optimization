public class Main {
    private static void printMemory(String label) {
        Runtime runtime = Runtime.getRuntime();

        long total = runtime.totalMemory();
        long free = runtime.freeMemory();

        System.out.printf(
                "%s: total=%d, free=%d, used=%d, max=%d bytes%n",
                label, total, free, total - free, runtime.maxMemory());
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("crash")) {
            NativeLab.crashInCallChain();
            return;
        }
        if (args.length > 0 && args[0].equals("memory")) {
            NativeLab.consumeMemory();
            return;
        }
        if (args.length > 0 && args[0].equals("pressure")) {
            NativeLab.pressureMemory();
            return;
        }
        long warmupAddress = NativeLab.allocateKilobyte();
        NativeLab.freeMemory(warmupAddress);
        printMemory("Warmup");

        printMemory("Before malloc");

        long address = NativeLab.allocateKilobyte();
        try {
            printMemory("After malloc");
        } finally {
            NativeLab.freeMemory(address);
        }

        printMemory("After free");
    }
}