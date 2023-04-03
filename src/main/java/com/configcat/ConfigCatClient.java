package com.configcat;

import com.google.gson.JsonElement;

import java9.util.function.Consumer;
import okhttp3.OkHttpClient;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import java9.util.concurrent.CompletableFuture;

/**
 * A client for handling configurations provided by ConfigCat.
 */
public final class ConfigCatClient implements ConfigurationProvider {
    private static final String BASE_URL_GLOBAL = "https://cdn-global.configcat.com";
    private static final String BASE_URL_EU = "https://cdn-eu.configcat.com";
    private static final Map<String, ConfigCatClient> INSTANCES = new HashMap<>();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ConfigCatLogger logger;
    private final RolloutEvaluator rolloutEvaluator;
    private final OverrideDataSource overrideDataSource;
    private final OverrideBehaviour overrideBehaviour;
    private final String sdkKey;
    private final ConfigCatHooks hooks;
    private User defaultUser;

    private ConfigService configService;

    private ConfigCatClient(String sdkKey, Options options) throws IllegalArgumentException {
        this.logger = new ConfigCatLogger(LoggerFactory.getLogger(ConfigCatClient.class), options.logLevel, options.hooks);

        this.sdkKey = sdkKey;
        this.overrideDataSource = options.overrideDataSource;
        this.overrideBehaviour = options.overrideBehaviour;
        this.hooks = options.hooks;
        this.defaultUser = options.defaultUser;
        this.rolloutEvaluator = new RolloutEvaluator(this.logger);

        if (this.overrideBehaviour != OverrideBehaviour.LOCAL_ONLY) {
            boolean hasCustomBaseUrl = options.baseUrl != null && !options.baseUrl.isEmpty();
            ConfigFetcher fetcher = new ConfigFetcher(options.httpClient == null
                    ? new OkHttpClient()
                    : options.httpClient,
                    this.logger,
                    sdkKey,
                    !hasCustomBaseUrl
                            ? options.dataGovernance == DataGovernance.GLOBAL
                            ? BASE_URL_GLOBAL
                            : BASE_URL_EU
                            : options.baseUrl,
                    hasCustomBaseUrl,
                    options.pollingMode.getPollingIdentifier());

            this.configService = new ConfigService(sdkKey, options.pollingMode, options.cache, logger, fetcher, options.hooks, options.offline);
        }
    }

    @Override
    public <T> T getValue(Class<T> classOfT, String key, T defaultValue) {
        return this.getValue(classOfT, key, null, defaultValue);
    }

    @Override
    public <T> T getValue(Class<T> classOfT, String key, User user, T defaultValue) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        if (classOfT != String.class &&
                classOfT != Integer.class &&
                classOfT != int.class &&
                classOfT != Double.class &&
                classOfT != double.class &&
                classOfT != Boolean.class &&
                classOfT != boolean.class)
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported.");

        try {
            return this.getValueAsync(classOfT, key, user, defaultValue).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return defaultValue;
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithDefaultValue("getValue", key, "defaultValue", defaultValue.toString()), e);
            return defaultValue;
        }
    }

    @Override
    public <T> CompletableFuture<T> getValueAsync(Class<T> classOfT, String key, T defaultValue) {
        return this.getValueAsync(classOfT, key, null, defaultValue);
    }

    @Override
    public <T> CompletableFuture<T> getValueAsync(Class<T> classOfT, String key, User user, T defaultValue) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        if (classOfT != String.class &&
                classOfT != Integer.class &&
                classOfT != int.class &&
                classOfT != Double.class &&
                classOfT != double.class &&
                classOfT != Boolean.class &&
                classOfT != boolean.class)
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported.");

        return this.getSettingsAsync()
                .thenApply(settingsResult -> this.getValueFromSettingsMap(classOfT, settingsResult, key, user, defaultValue));
    }

    @Override
    public <T> EvaluationDetails<T> getValueDetails(Class<T> classOfT, String key, T defaultValue) {
        return this.getValueDetails(classOfT, key, null, defaultValue);
    }

    @Override
    public <T> EvaluationDetails<T> getValueDetails(Class<T> classOfT, String key, User user, T defaultValue) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        if (classOfT != String.class &&
                classOfT != Integer.class &&
                classOfT != int.class &&
                classOfT != Double.class &&
                classOfT != double.class &&
                classOfT != Boolean.class &&
                classOfT != boolean.class)
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported.");

        try {
            return this.getValueDetailsAsync(classOfT, key, user, defaultValue).get();
        } catch (InterruptedException e) {
            String error = "Thread interrupted.";
            this.logger.error(0, error, e);
            Thread.currentThread().interrupt();
            return EvaluationDetails.fromError(key, defaultValue, error + ": " + e.getMessage(), user);
        } catch (Exception e) {
            this.logger.error(2001, ConfigCatLogMessages.getSettingEvaluationFailedForOtherReason(key, "defaultValue", defaultValue), e);
            return EvaluationDetails.fromError(key, defaultValue, e.getMessage(), user);
        }
    }

    @Override
    public <T> CompletableFuture<EvaluationDetails<T>> getValueDetailsAsync(Class<T> classOfT, String key, T defaultValue) {
        return this.getValueDetailsAsync(classOfT, key, null, defaultValue);
    }

    @Override
    public <T> CompletableFuture<EvaluationDetails<T>> getValueDetailsAsync(Class<T> classOfT, String key, User user, T defaultValue) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        if (classOfT != String.class &&
                classOfT != Integer.class &&
                classOfT != int.class &&
                classOfT != Double.class &&
                classOfT != double.class &&
                classOfT != Boolean.class &&
                classOfT != boolean.class)
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported.");

        return this.getSettingsAsync()
                .thenApply(settingsResult -> this.evaluate(classOfT, settingsResult.settings().get(key),
                        key, user != null ? user : this.defaultUser, settingsResult.fetchTime()));
    }

    @Override
    public String getVariationId(String key, String defaultVariationId) {
        return this.getVariationId(key, null, defaultVariationId);
    }

    @Override
    public String getVariationId(String key, User user, String defaultVariationId) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        try {
            return this.getVariationIdAsync(key, user, defaultVariationId).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return defaultVariationId;
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithDefaultValue("getVariationId", key, "defaultVariationId", defaultVariationId), e);
            return defaultVariationId;
        }
    }

    @Override
    public CompletableFuture<String> getVariationIdAsync(String key, String defaultVariationId) {
        return this.getVariationIdAsync(key, null, defaultVariationId);
    }

    @Override
    public CompletableFuture<String> getVariationIdAsync(String key, User user, String defaultVariationId) {
        if (key == null || key.isEmpty())
            throw new IllegalArgumentException("'key' cannot be null or empty.");

        return this.getSettingsAsync()
                .thenApply(settingsResult -> this.getVariationIdFromSettingsMap(settingsResult, key, user, defaultVariationId));
    }

    @Override
    public Collection<String> getAllVariationIds() {
        return this.getAllVariationIds(null);
    }

    @Override
    public CompletableFuture<Collection<String>> getAllVariationIdsAsync() {
        return this.getAllVariationIdsAsync(null);
    }

    @Override
    public Collection<String> getAllVariationIds(User user) {
        try {
            return this.getAllVariationIdsAsync(user).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllVariationIds", "empty array"), e);
            return new ArrayList<>();
        }
    }

    @Override
    public CompletableFuture<Collection<String>> getAllVariationIdsAsync(User user) {
        return this.getSettingsAsync()
                .thenApply(settingsResult -> {
                    try {
                        User userObject = user != null ? user : this.defaultUser;
                        Map<String, Setting> settingMap = settingsResult.settings();
                        Collection<String> keys = settingMap.keySet();
                        ArrayList<String> result = new ArrayList<>();

                        for (String key : keys) {
                            result.add(this.getVariationIdFromSettingsMap(settingsResult, key, userObject, null));
                        }

                        return result;
                    } catch (Exception e) {
                        this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllVariationIdsAsync", "empty array"), e);
                        return new ArrayList<>();
                    }
                });
    }

    @Override
    public Map<String, Object> getAllValues(User user) {
        try {
            return this.getAllValuesAsync(user).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return new HashMap<>();
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllValues", "empty map"), e);
            return new HashMap<>();
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> getAllValuesAsync(User user) {
        return this.getSettingsAsync()
                .thenApply(settingsResult -> {
                    try {
                        User userObject = user != null ? user : this.defaultUser;
                        Map<String, Setting> settingMap = settingsResult.settings();
                        Collection<String> keys = settingMap.keySet();
                        Map<String, Object> result = new HashMap<>();

                        for (String key : keys) {
                            Setting setting = settingMap.get(key);
                            if (setting == null) continue;
                            Object value = this.evaluate(classBySettingType(setting.getType()), setting, key, userObject, settingsResult.fetchTime()).getValue();
                            result.put(key, value);
                        }

                        return result;
                    } catch (Exception e) {
                        this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllValuesAsync", "empty map"), e);
                        return new HashMap<>();
                    }
                });
    }

    @Override
    public List<EvaluationDetails<?>> getAllValueDetails(User user) {
        try {
            return this.getAllValueDetailsAsync(user).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllValueDetails", "empty list"), e);
            return new ArrayList<>();
        }
    }

    @Override
    public CompletableFuture<List<EvaluationDetails<?>>> getAllValueDetailsAsync(User user) {
        return this.getSettingsAsync()
                .thenApply(settingResult -> {
                    try {
                        Map<String, Setting> settings = settingResult.settings();
                        List<EvaluationDetails<?>> result = new ArrayList<>();

                        for (String key : settings.keySet()) {
                            Setting setting = settings.get(key);

                            EvaluationDetails<?> evaluationDetails = this.evaluate(this.classBySettingType(setting.getType()), setting,
                                    key, user != null ? user : this.defaultUser, settingResult.fetchTime());
                            result.add(evaluationDetails);
                        }

                        return result;
                    } catch (Exception e) {
                        this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllValueDetailsAsync", "empty list"), e);
                        return new ArrayList<>();
                    }
                });
    }

    @Override
    public <T> Map.Entry<String, T> getKeyAndValue(Class<T> classOfT, String variationId) {
        if (variationId == null || variationId.isEmpty())
            throw new IllegalArgumentException("'variationId' cannot be null or empty.");

        try {
            return this.getKeyAndValueAsync(classOfT, variationId).get();
        } catch (InterruptedException e) {
            this.logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getKeyAndValue", "null"), e);
            return null;
        }
    }

    @Override
    public <T> CompletableFuture<Map.Entry<String, T>> getKeyAndValueAsync(Class<T> classOfT, String variationId) {
        if (variationId == null || variationId.isEmpty())
            throw new IllegalArgumentException("'variationId' cannot be null or empty.");

        return this.getSettingsAsync()
                .thenApply(settingsResult -> this.getKeyAndValueFromSettingsMap(classOfT, settingsResult.settings(), variationId));
    }

    @Override
    public Collection<String> getAllKeys() {
        try {
            return this.getAllKeysAsync().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.logger.error(0, "Thread interrupted.", e);
            return new ArrayList<>();
        } catch (Exception e) {
            this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllKeys", "empty array"), e);
            return new ArrayList<>();
        }
    }

    @Override
    public CompletableFuture<Collection<String>> getAllKeysAsync() {
        return this.getSettingsAsync()
                .thenApply(settingsResult -> {
                    try {
                        return settingsResult.settings().keySet();
                    } catch (Exception e) {
                        this.logger.error(1002, ConfigCatLogMessages.getSettingEvaluationErrorWithEmptyValue("getAllKeysAsync", "empty array"), e);
                        return new ArrayList<>();
                    }
                });
    }

    @Override
    public RefreshResult forceRefresh() {
        try {
            return forceRefreshAsync().get();
        } catch (InterruptedException e) {
            logger.error(0, "Thread interrupted.", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            this.logger.error(1003, ConfigCatLogMessages.getForceRefreshError("forceRefresh"), e);
        }
        return new RefreshResult(false, "An error occurred during the refresh.");
    }

    @Override
    public CompletableFuture<RefreshResult> forceRefreshAsync() {
        if (configService == null) {
            return CompletableFuture.completedFuture(new RefreshResult(false,
                    "The ConfigCat SDK is in local-only mode. Calling .forceRefresh() has no effect."));
        }

        return configService.refresh();
    }

    @Override
    public void setDefaultUser(User user) {
        if (isClosed()) {
            logger.warn(3201, ConfigCatLogMessages.getConfigServiceMethodHasNoEffectDueToClosedClient("setDefaultUser"));
            return;
        }
        this.defaultUser = user;
    }

    @Override
    public void clearDefaultUser() {
        if (isClosed()) {
            logger.warn(3201, ConfigCatLogMessages.getConfigServiceMethodHasNoEffectDueToClosedClient("clearDefaultUser"));
            return;
        }
        this.defaultUser = null;
    }

    public void setOnline() {
        if (this.configService != null && !isClosed()) {
            this.configService.setOnline();
        } else {
            logger.warn(3201, ConfigCatLogMessages.getConfigServiceMethodHasNoEffectDueToClosedClient("setOnline"));
        }
    }

    @Override
    public void setOffline() {
        if (this.configService != null && !isClosed()) {
            this.configService.setOffline();
        } else {
            logger.warn(3201, ConfigCatLogMessages.getConfigServiceMethodHasNoEffectDueToClosedClient("setOffline"));
        }
    }

    @Override
    public boolean isOffline() {
        return this.configService == null || this.configService.isOffline();
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    @Override
    public ConfigCatHooks getHooks() {
        return this.hooks;
    }

    @Override
    public void close() throws IOException {
        if (!this.isClosed.compareAndSet(false, true)) {
            return;
        }
        closeResources();
        synchronized (INSTANCES) {
            if (INSTANCES.get(this.sdkKey) == this) {
                INSTANCES.remove(this.sdkKey);
            }
        }
    }

    private void closeResources() throws IOException {
        if (configService != null) configService.close();
        this.hooks.clear();
    }

    private CompletableFuture<SettingResult> getSettingsAsync() {
        if (this.overrideBehaviour != null) {
            switch (this.overrideBehaviour) {
                case LOCAL_ONLY:
                    return CompletableFuture.completedFuture(new SettingResult(this.overrideDataSource.getLocalConfiguration(), Constants.DISTANT_PAST));
                case REMOTE_OVER_LOCAL:
                    if (configService == null)
                        return CompletableFuture.completedFuture(new SettingResult(this.overrideDataSource.getLocalConfiguration(), Constants.DISTANT_PAST));
                    return configService.getSettings()
                            .thenApply(settingResult -> {
                                Map<String, Setting> localSettings = new HashMap<>(this.overrideDataSource.getLocalConfiguration());
                                localSettings.putAll(settingResult.settings());
                                return new SettingResult(localSettings, settingResult.fetchTime());
                            });
                case LOCAL_OVER_REMOTE:
                    if (configService == null)
                        return CompletableFuture.completedFuture(new SettingResult(this.overrideDataSource.getLocalConfiguration(), Constants.DISTANT_PAST));
                    return configService.getSettings()
                            .thenApply(settingResult -> {
                                Map<String, Setting> localSettings = this.overrideDataSource.getLocalConfiguration();
                                Map<String, Setting> remoteSettings = new HashMap<>(settingResult.settings());
                                remoteSettings.putAll(localSettings);
                                return new SettingResult(remoteSettings, settingResult.fetchTime());
                            });
            }
        }

        return configService == null
                ? CompletableFuture.completedFuture(new SettingResult(new HashMap<>(), Constants.DISTANT_PAST))
                : configService.getSettings();
    }

    private <T> T getValueFromSettingsMap(Class<T> classOfT, SettingResult settingResult, String key, User user, T defaultValue) {
        User userObject = user != null ? user : this.defaultUser;
        try {
            Map<String, Setting> settings = settingResult.settings();
            if (settings.isEmpty()) {
                String error = ConfigCatLogMessages.getConfigJsonIsNotPresentedWitDefaultValue("defaultValue", defaultValue);
                this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, defaultValue, error, userObject));
                this.logger.error(1000, error);
                return defaultValue;
            }

            Setting setting = settings.get(key);
            if (setting == null) {
                String error = ConfigCatLogMessages.getSettingEvaluationFailedDueToMissingKey(key, "defaultValue", defaultValue, settings.keySet());
                this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, defaultValue, error, userObject));
                this.logger.error(1001, error);
                return defaultValue;
            }

            return this.evaluate(classOfT, setting, key, userObject, settingResult.fetchTime()).getValue();
        } catch (Exception e) {
            String error = ConfigCatLogMessages.getSettingEvaluationFailedForOtherReason(key, "defaultValue", defaultValue);
            this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, defaultValue, error + " " + e.getMessage(), userObject));
            this.logger.error(2001, error, e);
            return defaultValue;
        }
    }

    private String getVariationIdFromSettingsMap(SettingResult settingResult, String key, User user, String defaultVariationId) {
        User userObject = user != null ? user : this.defaultUser;
        try {
            Map<String, Setting> settings = settingResult.settings();
            if (settings.isEmpty()) {
                String error = ConfigCatLogMessages.getConfigJsonIsNotPresentedWitDefaultValue("defaultVariationId", defaultVariationId);
                this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, null, error, userObject));
                this.logger.error(1000, error);
                return defaultVariationId;
            }

            Setting setting = settings.get(key);
            if (setting == null) {
                String error = ConfigCatLogMessages.getSettingEvaluationFailedDueToMissingKey(key, "defaultVariationId", defaultVariationId, settings.keySet());
                this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, null, error, userObject));
                this.logger.error(1001, error);
                return defaultVariationId;
            }

            return this.rolloutEvaluator.evaluate(setting, key, userObject).variationId;
        } catch (Exception e) {
            String error = ConfigCatLogMessages.getSettingEvaluationFailedForOtherReason(key, "defaultVariationId", defaultVariationId);
            this.hooks.invokeOnFlagEvaluated(EvaluationDetails.fromError(key, null, error + " " + e.getMessage(), userObject));
            this.logger.error(2001, error, e);
            return defaultVariationId;
        }
    }

    private <T> Map.Entry<String, T> getKeyAndValueFromSettingsMap(Class<T> classOfT, Map<String, Setting> settings, String variationId) {
        try {
            if (settings.isEmpty()) {
                this.logger.error(1000, ConfigCatLogMessages.getConfigJsonIsNotPresentedWitEmptyResult("null"));
                return null;
            }

            for (Map.Entry<String, Setting> node : settings.entrySet()) {
                String settingKey = node.getKey();
                Setting setting = node.getValue();
                if (variationId.equals(setting.getVariationId())) {
                    return new AbstractMap.SimpleEntry<>(settingKey, (T) this.parseObject(classOfT, setting.getValue()));
                }

                for (RolloutRule rolloutRule : setting.getRolloutRules()) {
                    if (variationId.equals(rolloutRule.getVariationId())) {
                        return new AbstractMap.SimpleEntry<>(settingKey, (T) this.parseObject(classOfT, rolloutRule.getValue()));
                    }
                }

                for (PercentageRule percentageRule : setting.getPercentageItems()) {
                    if (variationId.equals(percentageRule.getVariationId())) {
                        return new AbstractMap.SimpleEntry<>(settingKey, (T) this.parseObject(classOfT, percentageRule.getValue()));
                    }
                }
            }

            return null;
        } catch (Exception e) {
            this.logger.error(2011, ConfigCatLogMessages.getSettingForVariationIdIsNotPresent(variationId));
            return null;
        }
    }

    private <T> EvaluationDetails<T> evaluate(Class<T> classOfT, Setting setting, String key, User user, Long fetchTime) {
        EvaluationResult evaluationResult = this.rolloutEvaluator.evaluate(setting, key, user);
        EvaluationDetails<Object> details = new EvaluationDetails<>(
                this.parseObject(classOfT, evaluationResult.value),
                key,
                evaluationResult.variationId,
                user,
                false,
                null,
                fetchTime,
                evaluationResult.targetingRule,
                evaluationResult.percentageRule);
        this.hooks.invokeOnFlagEvaluated(details);
        return details.asTypeSpecific();
    }

    private Object parseObject(Class<?> classOfT, JsonElement element) {
        if (classOfT == String.class)
            return element.getAsString();
        else if (classOfT == Integer.class || classOfT == int.class)
            return element.getAsInt();
        else if (classOfT == Double.class || classOfT == double.class)
            return element.getAsDouble();
        else if (classOfT == Boolean.class || classOfT == boolean.class)
            return element.getAsBoolean();
        else
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported");
    }

    private Class<?> classBySettingType(SettingType settingType) {
        if (settingType == SettingType.BOOLEAN)
            return boolean.class;
        else if (settingType == SettingType.STRING)
            return String.class;
        else if (settingType == SettingType.INT)
            return int.class;
        else if (settingType == SettingType.DOUBLE)
            return double.class;
        else
            throw new IllegalArgumentException("Only String, Integer, Double or Boolean types are supported");
    }

    /**
     * Creates a new or gets an already existing ConfigCatClient for the given sdkKey.
     *
     * @param sdkKey the SDK Key for to communicate with the ConfigCat services.
     * @return the ConfigCatClient instance.
     */
    public static ConfigCatClient get(String sdkKey) {
        return get(sdkKey, null);
    }

    /**
     * Creates a new or gets an already existing ConfigCatClient for the given sdkKey.
     *
     * @param sdkKey          the SDK Key for to communicate with the ConfigCat services.
     * @param optionsCallback the options callback to set up the created ConfigCatClient instance.
     * @return the ConfigCatClient instance.
     */
    public static ConfigCatClient get(String sdkKey, Consumer<Options> optionsCallback) {
        if (sdkKey == null || sdkKey.isEmpty())
            throw new IllegalArgumentException("'sdkKey' cannot be null or empty.");

        synchronized (INSTANCES) {
            ConfigCatClient existing = INSTANCES.get(sdkKey);
            if (existing != null) {
                if (optionsCallback != null) {
                    existing.logger.warn(3000, ConfigCatLogMessages.getClientIsAlreadyCreated(sdkKey));
                }
                return existing;
            }

            ConfigCatClient client;
            if (optionsCallback != null) {
                Options options = new Options();
                optionsCallback.accept(options);
                client = new ConfigCatClient(sdkKey, options);
            } else {
                client = new ConfigCatClient(sdkKey, new Options());
            }

            INSTANCES.put(sdkKey, client);
            return client;
        }
    }

    /**
     * Closes all ConfigCatClient instances.
     */
    public static void closeAll() throws IOException {
        synchronized (INSTANCES) {
            for (ConfigCatClient client : INSTANCES.values()) {
                client.closeResources();
            }
            INSTANCES.clear();
        }
    }

    /**
     * Configuration options for a {@link ConfigCatClient} instance.
     */
    public static class Options {
        private OkHttpClient httpClient;
        private ConfigCache cache = new NullConfigCache();
        private String baseUrl;
        private PollingMode pollingMode = PollingModes.autoPoll(60);
        private LogLevel logLevel = LogLevel.WARNING;
        private DataGovernance dataGovernance = DataGovernance.GLOBAL;
        private OverrideDataSource overrideDataSource;
        private OverrideBehaviour overrideBehaviour;
        private User defaultUser;
        private boolean offline;

        private final ConfigCatHooks hooks = new ConfigCatHooks();

        /**
         * Sets the underlying http client which will be used to fetch the latest configuration.
         *
         * @param httpClient the http client.
         */
        public void httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        /**
         * Sets the internal cache implementation.
         *
         * @param cache a {@link ConfigCache} implementation used to cache the configuration.
         */
        public void cache(ConfigCache cache) {
            this.cache = cache;
        }

        /**
         * Sets the base ConfigCat CDN url.
         *
         * @param baseUrl the base ConfigCat CDN url.
         */
        public void baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Sets the internal refresh policy implementation.
         *
         * @param pollingMode the polling mode.
         */
        public void pollingMode(PollingMode pollingMode) {
            this.pollingMode = pollingMode;
        }

        /**
         * Default: Global. Set this parameter to be in sync with the Data Governance preference on the Dashboard:
         * https://app.configcat.com/organization/data-governance (Only Organization Admins have access)
         *
         * @param dataGovernance the {@link DataGovernance} parameter.
         */
        public void dataGovernance(DataGovernance dataGovernance) {
            this.dataGovernance = dataGovernance;
        }

        /**
         * Default: Warning. Sets the internal log level.
         *
         * @param logLevel the {@link LogLevel} parameter.
         */
        public void logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
        }

        /**
         * Sets feature flag and setting overrides.
         *
         * @param overrideDataSource the feature flag and setting overrides' data source.
         * @param behaviour          the override behaviour. It can be used to set preference on whether the local values should
         *                           override the remote values, or use local values only when a remote value doesn't exist,
         *                           or use it for local only mode.
         * @throws IllegalArgumentException when the dataSourceBuilder or behaviour parameter is null.
         */
        public void flagOverrides(OverrideDataSource overrideDataSource, OverrideBehaviour behaviour) {
            if (overrideDataSource == null) {
                throw new IllegalArgumentException("'overrideDataSource' cannot be null.");
            }

            if (behaviour == null) {
                throw new IllegalArgumentException("'behaviour' cannot be null.");
            }

            this.overrideDataSource = overrideDataSource;
            this.overrideBehaviour = behaviour;
        }

        /**
         * The default user, used as fallback when there's no user parameter is passed to the getValue() method.
         *
         * @param user the default user.
         */
        public void defaultUser(User user) {
            this.defaultUser = user;
        }

        /**
         * Indicates whether the SDK should be initialized in offline mode or not.
         *
         * @param isOffline true when the SDK should be initialized in offline mode, otherwise false.
         */
        public void offline(boolean isOffline) {
            this.offline = isOffline;
        }

        /**
         * Hooks for events sent by ConfigCatClient.
         *
         * @return the hooks object used to subscribe to SDK events.
         */
        public ConfigCatHooks hooks() {
            return this.hooks;
        }
    }

}