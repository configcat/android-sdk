package com.configcat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.*;

class LoggerTest {
    @Test
    void debug() {
        Logger mockLogger = mock(Logger.class);
        ConfigCatLogger logger = new ConfigCatLogger(mockLogger, LogLevel.DEBUG);

        logger.debug("debug");
        logger.info(5000, "info");
        logger.warn(3000, "warn");
        logger.error(1000, "error", new Exception());

        verify(mockLogger, times(1)).debug("[0] debug");
        verify(mockLogger, times(1)).error(anyString(), eq(1000),eq("error"), any(Exception.class));
        verify(mockLogger, times(1)).warn(anyString(), eq(3000),eq("warn"));
        verify(mockLogger, times(1)).info(anyString(), eq(5000), eq("info"));
    }

    @Test
    void info() {
        Logger mockLogger = mock(Logger.class);
        ConfigCatLogger logger = new ConfigCatLogger(mockLogger, LogLevel.INFO);

        logger.debug("debug");
        logger.info(5000, "info");
        logger.warn(3000, "warn");
        logger.error(1000, "error", new Exception());

        verify(mockLogger, never()).debug("[0] debug");
        verify(mockLogger, times(1)).error(anyString(), eq(1000),eq("error"), any(Exception.class));
        verify(mockLogger, times(1)).warn(anyString(), eq(3000),eq("warn"));
        verify(mockLogger, times(1)).info(anyString(), eq(5000), eq("info"));
    }

    @Test
    void warn() {
        Logger mockLogger = mock(Logger.class);
        ConfigCatLogger logger = new ConfigCatLogger(mockLogger, LogLevel.WARNING);

        logger.debug("debug");
        logger.info(5000, "info");
        logger.warn(3000, "warn");
        logger.error(1000, "error", new Exception());

        verify(mockLogger, never()).debug("[0] debug");
        verify(mockLogger, never()).info(anyString(), eq(5000), eq("info"));
        verify(mockLogger, times(1)).warn(anyString(), eq(3000),eq("warn"));
        verify(mockLogger, times(1)).error(anyString(), eq(1000),eq("error"), any(Exception.class));
    }

    @Test
    void error() {
        Logger mockLogger = mock(Logger.class);
        ConfigCatLogger logger = new ConfigCatLogger(mockLogger, LogLevel.ERROR);

        logger.debug("debug");
        logger.info(5000, "info");
        logger.warn(3000, "warn");
        logger.error(1000, "error", new Exception());

        verify(mockLogger, never()).debug("[0] debug");
        verify(mockLogger, never()).info(anyString(), eq(5000), eq("info"));
        verify(mockLogger, never()).warn(anyString(), eq(3000),eq("warn"));
        verify(mockLogger, times(1)).error(anyString(), eq(1000),eq("error"), any(Exception.class));
    }

    @Test
    void noLog() {
        Logger mockLogger = mock(Logger.class);
        ConfigCatLogger logger = new ConfigCatLogger(mockLogger, LogLevel.NO_LOG);

        logger.debug("debug");
        logger.info(5000, "info");
        logger.warn(3000, "warn");
        logger.error(1000, "error");

        verify(mockLogger, never()).debug("[0] debug");
        verify(mockLogger, never()).info("[5000] info");
        verify(mockLogger, never()).warn("[3000] warn");
        verify(mockLogger, never()).error("[1000] error");
    }
}
