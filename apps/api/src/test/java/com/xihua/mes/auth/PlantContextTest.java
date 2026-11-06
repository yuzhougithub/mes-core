package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.xihua.mes.common.PlantNotSetException;

class PlantContextTest {

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    @Test
    void setGetClear() {
        assertNull(PlantContext.get());
        PlantContext.set("plant-a");
        assertEquals("plant-a", PlantContext.get());
        assertEquals("plant-a", CurrentContext.plantId());
        PlantContext.clear();
        assertNull(PlantContext.get());
    }

    @Test
    void setRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> PlantContext.set(null));
        assertThrows(IllegalArgumentException.class, () -> PlantContext.set("   "));
    }

    @Test
    void requireWhenSet() {
        PlantContext.set("plant-b");
        assertEquals("plant-b", PlantContext.require());
        assertEquals("plant-b", CurrentContext.requirePlantId());
    }

    @Test
    void requireWhenUnsetThrows() {
        PlantNotSetException ex = assertThrows(PlantNotSetException.class, PlantContext::require);
        assertTrue(ex.getMessage().contains("X-Plant-Id"));
    }

    @Test
    void currentContextUserId() {
        assertNull(CurrentContext.userId());
        CurrentContext.setUserId("user-1");
        assertEquals("user-1", CurrentContext.userId());
        CurrentContext.clear();
        assertNull(CurrentContext.userId());
        assertNull(CurrentContext.plantId());
    }

    @Test
    void threadLocalIsIsolated() throws InterruptedException {
        PlantContext.set("main-plant");
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> otherGet = new AtomicReference<>("unset");
        AtomicReference<String> otherRequireError = new AtomicReference<>();

        Thread other = new Thread(() -> {
            try {
                otherGet.set(PlantContext.get());
                try {
                    PlantContext.require();
                } catch (PlantNotSetException e) {
                    otherRequireError.set(e.getMessage());
                }
                PlantContext.set("other-plant");
                assertEquals("other-plant", PlantContext.get());
            } finally {
                PlantContext.clear();
                ready.countDown();
                try {
                    done.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        other.start();
        ready.await();
        assertEquals("main-plant", PlantContext.get());
        assertNull(otherGet.get());
        assertTrue(otherRequireError.get() != null && otherRequireError.get().contains("X-Plant-Id"));
        done.countDown();
        other.join();
        assertEquals("main-plant", PlantContext.get());
    }
}
