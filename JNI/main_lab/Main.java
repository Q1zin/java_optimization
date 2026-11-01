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
        if (args.length > 0 && args[0].equals("strings")) {
            String[] examples = { "Hello", "Привет", "😀", "" };

            for (String text : examples) {
                System.out.printf(
                        "\"%s\": Java=%d, JNI=%d%n",
                        text, text.length(), NativeLab.stringLength(text));
            }

            try {
                NativeLab.stringLength(null);
            } catch (NullPointerException exception) {
                System.out.println("null: " + exception);
            }

            return;
        }
        if (args.length > 0 && args[0].equals("call")) {
            Bean bean = new Bean();
            bean.setIntValue(228);

            NativeLab.callBeanMethod(bean);
            return;
        }
        if (args.length > 0 && args[0].equals("field")) {
            Bean bean = new Bean();
            bean.setIntValue(42);

            System.out.println("Before: " + bean.getIntValue());

            NativeLab.changeBeanInt(bean, 100);

            System.out.println("After: " + bean.getIntValue());
            return;
        }
        if (args.length > 0 && args[0].equals("struct")) {
            long address = NativeLab.createNativeData(42);

            try {
                System.out.printf("Address: 0x%x%n", address);
                System.out.println("Value: " + NativeLab.readNativeData(address));
            } finally {
                NativeLab.freeNativeData(address);
            }

            System.out.println("NativeData freed");
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