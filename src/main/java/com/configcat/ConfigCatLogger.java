package com.configcat;

import org.slf4j.Logger;

class ConfigCatLogger {
    private final Logger logger;
    private final LogLevel logLevel;
    private final ConfigCatHooks hooks;

    public ConfigCatLogger(Logger logger, LogLevel logLevel, ConfigCatHooks hooks) {
        this.logger = logger;
        this.logLevel = logLevel;
        this.hooks = hooks;
    }

    public ConfigCatLogger(Logger logger, LogLevel logLevel) {
        this.logger = logger;
        this.logLevel = logLevel;
        this.hooks = null;
    }

    public ConfigCatLogger(Logger logger) {
        this.logger = logger;
        this.logLevel = LogLevel.WARNING;
        this.hooks = null;
    }

    public void warn(int eventId, String message) {
        if (this.logLevel.ordinal() <= LogLevel.WARNING.ordinal()) {
            this.logger.warn("[{}] {}", eventId, message);
        }
    }

    public void error(int eventId, String message, Exception exception) {
        if (this.hooks != null) this.hooks.invokeOnError(message);
        if (this.logLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            this.logger.error("[{}] {}", eventId, message, exception);
        }
    }

    public void error(int eventId, String message) {
        if (this.hooks != null) this.hooks.invokeOnError(message);
        if (this.logLevel.ordinal() <= LogLevel.ERROR.ordinal()) {
            this.logger.error("[{}] {}", eventId, message);
        }
    }

    public void info(int eventId, String message) {
        if (this.logLevel.ordinal() <= LogLevel.INFO.ordinal()) {
            this.logger.info("[{}] {}", eventId, message);
        }
    }

    public void debug(String message) {
        if (this.logLevel.ordinal() <= LogLevel.DEBUG.ordinal()) {
            this.logger.debug("[{}] {}", 0, message);
        }
    }
}
