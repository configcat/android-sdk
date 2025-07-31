package com.configcat;

import org.slf4j.Logger;

class ConfigCatLogger {
    private final Logger logger;
    private final LogLevel logLevel;
    private final ConfigCatHooks hooks;
    private final LogFilterFunction filterFunction;

    public ConfigCatLogger(Logger logger, LogLevel logLevel, ConfigCatHooks hooks, LogFilterFunction filterFunction ) {
        this.logger = logger;
        this.logLevel = logLevel;
        this.hooks = hooks;
        this.filterFunction  = filterFunction;
    }

    public ConfigCatLogger(Logger logger, LogLevel logLevel, ConfigCatHooks hooks) {
        this.logger = logger;
        this.logLevel = logLevel;
        this.hooks = hooks;
        this.filterFunction  = null;
    }

    public ConfigCatLogger(Logger logger, LogLevel logLevel) {
        this.logger = logger;
        this.logLevel = logLevel;
        this.hooks = null;
        this.filterFunction = null;
    }

    public ConfigCatLogger(Logger logger) {
        this.logger = logger;
        this.logLevel = LogLevel.WARNING;
        this.hooks = null;
        this.filterFunction = null;
    }

    public void warn(int eventId, Object message) {
        if (filter(eventId,  LogLevel.WARNING, message, null)) {
            this.logger.warn("[{}] {}", eventId, message);
        }
    }

    public void error(int eventId, Object message, Throwable exception) {
        if (this.hooks != null) this.hooks.invokeOnError(message);
        if (filter(eventId,  LogLevel.ERROR, message, exception)) {
            this.logger.error("[{}] {} {}", eventId, message, exception.getMessage(), exception);
        }
    }

    public void error(int eventId, Object message) {
        if (this.hooks != null) this.hooks.invokeOnError(message);
        if (filter(eventId,  LogLevel.ERROR, message, null)) {
            this.logger.error("[{}] {}", eventId, message);
        }
    }

    public void info(int eventId, Object message) {
        if (filter(eventId,  LogLevel.INFO, message, null)) {
            this.logger.info("[{}] {}", eventId, message);
        }
    }

    public void debug(String message) {
        if (filter(0,  LogLevel.DEBUG, message, null)) {
            this.logger.debug("[{}] {}", 0, message);
        }
    }

    private boolean filter(int eventId, LogLevel logLevel, Object message, Throwable exception) {
        return this.logLevel.ordinal() <= logLevel.ordinal() && (this.filterFunction == null || this.filterFunction.apply(logLevel, eventId, message, exception));
    }
}
