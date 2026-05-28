package com.configcat;

import java9.util.function.Consumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TestStateMonitor implements StateMonitor {
    private final List<Consumer<Boolean>> stateChangeListeners = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean state = new AtomicBoolean(true);

    public void setState(boolean state) {
        this.state.set(state);
    }

    public void notifyListeners() {
        lock.readLock().lock();
        try {
            for (Consumer<Boolean> listener : this.stateChangeListeners) {
                listener.accept(this.state.get());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void addStateChangeListener(Consumer<Boolean> listener) {
        lock.writeLock().lock();
        try {
            this.stateChangeListeners.add(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isNetworkAvailable() {
        return this.state.get();
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            this.stateChangeListeners.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
