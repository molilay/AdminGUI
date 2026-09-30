package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BoundedConcurrentDequeTest {

    @Test
    void concurrentWritersNeverExceedBoundAndSnapshotsAreImmutable() throws Exception {
        BoundedConcurrentDeque<Integer> buffer = new BoundedConcurrentDeque<>(64);
        int workers = 8;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int base = worker * 1000;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < 1000; index++) buffer.addLast(base + index);
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        List<Integer> snapshot = buffer.snapshot();
        assertEquals(64, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(1));
        buffer.resize(10);
        assertEquals(10, buffer.size());
        assertEquals(64, snapshot.size(), "an existing snapshot must not mutate");
    }
}
