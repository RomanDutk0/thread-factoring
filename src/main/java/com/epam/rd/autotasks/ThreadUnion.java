package com.epam.rd.autotasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadUnion implements ThreadFactory {
    private final String name;
    private final AtomicInteger threadCount = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final List<FinishedThreadResult> results = Collections.synchronizedList(new ArrayList<>());
    private final ReentrantLock lock = new ReentrantLock();

    private ThreadUnion(String name) {
        this.name = name;
    }

    public static ThreadUnion newInstance(String name) {
        return new ThreadUnion(name);
    }


    public Thread newThread(Runnable runnable) {
        if (shutdown.get()) {
            throw new IllegalStateException("ThreadUnion is shut down. No new threads can be created.");
        }

        String threadName = name + "-worker-" + threadCount.getAndIncrement();
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
                results.add(new FinishedThreadResult(threadName));
            } catch (Throwable e) {
                results.add(new FinishedThreadResult(threadName, e));
            }
        });
        thread.setName(threadName);
        threads.add(thread);
        return thread;
    }


    public int totalSize() {
        return threadCount.get();
    }


    public int activeSize() {
        return (int) threads.stream().filter(Thread::isAlive).count();
    }


    public void shutdown() {
        lock.lock();
        try {
            if (shutdown.compareAndSet(false, true)) {
                threads.forEach(Thread::interrupt);
            }
        } finally {
            lock.unlock();
        }
    }


    public boolean isShutdown() {
        return shutdown.get();
    }


    public void awaitTermination() {
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }


    public boolean isFinished() {
        return shutdown.get() && threads.stream().noneMatch(Thread::isAlive);
    }


    public List<FinishedThreadResult> results() {
        return new ArrayList<>(results);
    }
}
