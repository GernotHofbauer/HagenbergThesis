package BACore.ThreadlocalAndScopedVariables;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.StructuredTaskScope;


public class MemoryUsageComparison {

    private static final int ARRAY_SIZE = 1024 * 1024;
    // Large array to simulate memory usage

    private static final InheritableThreadLocal<byte[]> threadLocal = new InheritableThreadLocal<>() {
        @Override
        protected byte[] initialValue() {
            return new byte[ARRAY_SIZE];
        }
    };

    private static final ScopedValue<byte[]> scopedValue = ScopedValue.newInstance();

    public static void main(String[] args) throws Exception {
        // Warmup der JVM
        System.out.println("Warming up JVM...");
        for (int i = 0; i < 5; i++) {
            measureThreadLocal(10);
            measureScopedValue(10);
            System.gc();
            Thread.sleep(100);
        }

        // Number of threads to spawn
        int threadCount = 1000;

        int measurements = 5;
        long[] threadLocalMemory = new long[measurements];
        long[] scopedValueMemory = new long[measurements];

        System.out.println("\nStarting measurements...");
        for (int i = 0; i < measurements; i++) {
            System.gc();
            Thread.sleep(1000);

            long baselineMemory = getUsedMemory();
            System.out.println();
            System.out.println(STR."Measurement \{i + 1}");
            System.out.println(STR."Baseline Memory: \{formatMemory(baselineMemory)}");

            // ThreadLocal Test
            long beforeThreadLocal = getUsedMemory();
            measureThreadLocal(threadCount);
            long afterThreadLocal = getUsedMemory();
            threadLocalMemory[i] = afterThreadLocal - beforeThreadLocal;
            System.out.println(STR."ThreadLocal Memory Usage: \{formatMemory(threadLocalMemory[i])}");

            System.gc();
            Thread.sleep(1000);

            // ScopedValue Test
            long beforeScoped = getUsedMemory();
            measureScopedValue(threadCount);
            long afterScoped = getUsedMemory();
            scopedValueMemory[i] = afterScoped - beforeScoped;
            System.out.println(STR."ScopedValue Memory Usage: \{formatMemory(scopedValueMemory[i])}");
        }

        System.out.println("\nFinal Results:");
        System.out.println(STR."Average ThreadLocal Memory Usage: \{formatMemory(average(threadLocalMemory))}");
        System.out.println(STR."Average ScopedValue Memory Usage: \{formatMemory(average(scopedValueMemory))}");
        System.out.println(STR."Memory Difference: \{formatMemory(Math.abs(average(threadLocalMemory) - average(scopedValueMemory)))}");
    }

    private static void measureThreadLocal(int threadCount) throws Exception {
        try (var scope = new StructuredTaskScope<Void>()) {
            for (int i = 0; i < threadCount; i++) {
                scope.fork(() -> {
                    byte[] data = threadLocal.get();
                    // Play around with the data
                    data[0] = (byte) System.nanoTime();
                    Thread.sleep(50);
                    return null;
                });
            }
            scope.join();
        }
    }

    private static void measureScopedValue(int threadCount) throws Exception {
        ScopedValue.where(scopedValue, new byte[ARRAY_SIZE]).run(() -> {
            try (var scope = new StructuredTaskScope<Void>()) {
                for (int i = 0; i < threadCount; i++) {
                    scope.fork(() -> {
                        byte[] data = scopedValue.get();
                        // Play around with the data
                        data[0] = (byte) System.nanoTime();
                        Thread.sleep(50);
                        return null;
                    });
                }
                scope.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static long getUsedMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        return memoryBean.getHeapMemoryUsage().getUsed() +
                memoryBean.getNonHeapMemoryUsage().getUsed();
    }

    private static String formatMemory(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private static long average(long[] values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}