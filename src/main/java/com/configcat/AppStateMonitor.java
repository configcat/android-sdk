package com.configcat;

import android.app.Activity;
import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import java9.util.function.Consumer;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

interface StateMonitor extends Closeable {
    void addStateChangeListener(Consumer<Boolean> listener);
    boolean isNetworkAvailable();
}

class AppStateMonitor extends BroadcastReceiver implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2, StateMonitor {
    static final String CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

    private final Context context;
    private final Application application;
    private final ConfigCatLogger logger;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final List<Consumer<Boolean>> stateChangeListeners = new ArrayList<>();
    private final AtomicBoolean inForeground = new AtomicBoolean(true);
    private final AtomicBoolean hasNetwork = new AtomicBoolean(true);

    public AppStateMonitor(Context context, ConfigCatLogger logger) {
        this.context = context;
        this.logger = logger;

        application = (Application) context.getApplicationContext();
        application.registerActivityLifecycleCallbacks(this);
        application.registerComponentCallbacks(this);

        IntentFilter filter = new IntentFilter(CONNECTIVITY_CHANGE);
        application.registerReceiver(this, filter);
    }

    public void addStateChangeListener(Consumer<Boolean> listener) {
        lock.writeLock().lock();
        try {
            this.stateChangeListeners.add(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void notifyListeners() {
        logger.debug(String.format("App state has been changed, in foreground: %s, has network: %s", inForeground.get(), hasNetwork.get()));
        lock.readLock().lock();
        try {
            for (Consumer<Boolean> listener : this.stateChangeListeners) {
                listener.accept(inForeground.get() && hasNetwork.get());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        if (inForeground.compareAndSet(false, true)) {
            notifyListeners();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (inForeground.compareAndSet(false, true)) {
            notifyListeners();
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (inForeground.compareAndSet(false, true)) {
            notifyListeners();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isConnected = isNetworkAvailable();
        if (isConnected && hasNetwork.compareAndSet(false, true)) {
            notifyListeners();
        } else if (!isConnected && hasNetwork.compareAndSet(true, false)) {
            notifyListeners();
        }
    }

    @Override
    public void onTrimMemory(int i) {
        if (i == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) { // We're in the background
            if (inForeground.compareAndSet(true, false)) {
                notifyListeners();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {}

    @Override
    public void onLowMemory() {}

    public boolean isNetworkAvailable() {
        ConnectivityManager conn =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            stateChangeListeners.clear();
        } finally {
            lock.writeLock().unlock();
        }
        application.unregisterReceiver(this);
        application.unregisterActivityLifecycleCallbacks(this);
        application.unregisterComponentCallbacks(this);
    }
}
