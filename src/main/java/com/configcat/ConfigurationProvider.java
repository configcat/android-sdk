package com.configcat;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java9.util.concurrent.CompletableFuture;

/**
 * Defines the public interface of the {@link ConfigCatClient}.
 */
public interface ConfigurationProvider extends Closeable {
    /**
     * Gets the value of a feature flag or setting as T identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return the configuration value identified by the given key.
     */
    <T> T getValue(Class<T> classOfT, String key, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param user         the user object to identify the caller.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return the configuration value identified by the given key.
     */
    <T> T getValue(Class<T> classOfT, String key, User user, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T asynchronously identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return a future which computes the configuration value identified by the given key.
     */
    <T> CompletableFuture<T> getValueAsync(Class<T> classOfT, String key, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T asynchronously identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param user         the user object to identify the caller.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return a future which computes the configuration value identified by the given key.
     */
    <T> CompletableFuture<T> getValueAsync(Class<T> classOfT, String key, User user, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return the evaluation details.
     */
    <T> EvaluationDetails<T> getValueDetails(Class<T> classOfT, String key, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param user         the user object to identify the caller.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return the evaluation details.
     */
    <T> EvaluationDetails<T> getValueDetails(Class<T> classOfT, String key, User user, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T asynchronously identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return a future which computes the the evaluation details.
     */
    <T> CompletableFuture<EvaluationDetails<T>> getValueDetailsAsync(Class<T> classOfT, String key, T defaultValue);

    /**
     * Gets the value of a feature flag or setting as T asynchronously identified by the given {@code key}.
     *
     * @param classOfT     the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param key          the identifier of the configuration value.
     * @param user         the user object to identify the caller.
     * @param defaultValue in case of any failure, this value will be returned.
     * @param <T>          the type of the desired config value.
     * @return a future which computes the the evaluation details.
     */
    <T> CompletableFuture<EvaluationDetails<T>> getValueDetailsAsync(Class<T> classOfT, String key, User user, T defaultValue);

    /**
     * Gets the Variation ID (analytics) of a feature flag or setting synchronously based on its key.
     *
     * @param key                the identifier of the configuration value.
     * @param defaultVariationId in case of any failure, this value will be returned.
     * @return the Variation ID.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getValueDetails() instead.
     */
    @Deprecated
    String getVariationId(String key, String defaultVariationId);

    /**
     * Gets the Variation ID (analytics) of a feature flag or setting synchronously based on its key.
     *
     * @param key                the identifier of the configuration value.
     * @param user               the user object to identify the caller.
     * @param defaultVariationId in case of any failure, this value will be returned.
     * @return the Variation ID.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getValueDetails() instead.
     */
    @Deprecated
    String getVariationId(String key, User user, String defaultVariationId);

    /**
     * Gets the Variation ID (analytics) of a feature flag or setting asynchronously based on its key.
     *
     * @param key                the identifier of the configuration value.
     * @param defaultVariationId in case of any failure, this value will be returned.
     * @return a future which computes the Variation ID.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getValueDetailsAsync() instead.
     */
    @Deprecated
    CompletableFuture<String> getVariationIdAsync(String key, String defaultVariationId);

    /**
     * Gets the Variation ID (analytics) of a feature flag or setting asynchronously based on its key.
     *
     * @param key                the identifier of the configuration value.
     * @param user               the user object to identify the caller.
     * @param defaultVariationId in case of any failure, this value will be returned.
     * @return a future which computes the Variation ID.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getValueDetailsAsync() instead.
     */
    @Deprecated
    CompletableFuture<String> getVariationIdAsync(String key, User user, String defaultVariationId);

    /**
     * Gets the Variation IDs (analytics) of all feature flags or settings synchronously.
     *
     * @return a collection of all Variation IDs.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getAllValueDetails() instead.
     */
    @Deprecated
    Collection<String> getAllVariationIds();

    /**
     * Gets the Variation IDs (analytics) of all feature flags or settings asynchronously.
     *
     * @return a future which computes the collection of all Variation IDs.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getAllValueDetailsAsync() instead.
     */
    @Deprecated
    CompletableFuture<Collection<String>> getAllVariationIdsAsync();

    /**
     * Gets the Variation IDs (analytics) of all feature flags or settings synchronously.
     *
     * @param user the user object to identify the caller.
     * @return a collection of all Variation IDs.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getAllValueDetails() instead.
     */
    @Deprecated
    Collection<String> getAllVariationIds(User user);

    /**
     * Gets the Variation IDs (analytics) of all feature flags or settings asynchronously.
     *
     * @param user the user object to identify the caller.
     * @return a future which computes the collection of all Variation IDs.
     * @deprecated This method is obsolete and will be removed in a future major version. Please use getAllValueDetailsAsync() instead.
     */
    @Deprecated
    CompletableFuture<Collection<String>> getAllVariationIdsAsync(User user);

    /**
     * Gets the values of all feature flags or settings synchronously.
     *
     * @param user the user object to identify the caller.
     * @return a collection of all values.
     */
    Map<String, Object> getAllValues(User user);

    /**
     * Gets the values of all feature flags or settings asynchronously.
     *
     * @param user the user object to identify the caller.
     * @return a future which computes the collection of all values.
     */
    CompletableFuture<Map<String, Object>> getAllValuesAsync(User user);

    /**
     * Gets the detailed values of all feature flags or settings synchronously.
     *
     * @param user the user object to identify the caller.
     * @return a collection of all detailed values.
     */
    List<EvaluationDetails<?>> getAllValueDetails(User user);

    /**
     * Gets the detailed values of all feature flags or settings asynchronously.
     *
     * @param user the user object to identify the caller.
     * @return a future which computes the collection of all detailed values.
     */
    CompletableFuture<List<EvaluationDetails<?>>> getAllValueDetailsAsync(User user);

    /**
     * Gets the key of a setting and its value identified by the given Variation ID (analytics).
     *
     * @param classOfT    the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param variationId the Variation ID.
     * @param <T>         the type of the desired config value.
     * @return the key of a setting and its value.
     */
    <T> Map.Entry<String, T> getKeyAndValue(Class<T> classOfT, String variationId);

    /**
     * Gets the key of a setting and its value identified by the given Variation ID (analytics).
     *
     * @param classOfT    the class of T. Only {@link String}, {@link Integer}, {@link Double} or {@link Boolean} types are supported.
     * @param variationId the Variation ID.
     * @param <T>         the type of the desired config value.
     * @return a future which computes the key of a setting and its value.
     */
    <T> CompletableFuture<Map.Entry<String, T>> getKeyAndValueAsync(Class<T> classOfT, String variationId);

    /**
     * Gets a collection of all setting keys.
     *
     * @return a collection of all setting keys.
     */
    Collection<String> getAllKeys();

    /**
     * Gets a collection of all setting keys asynchronously.
     *
     * @return a collection of all setting keys.
     */
    CompletableFuture<Collection<String>> getAllKeysAsync();

    /**
     * Initiates a force refresh synchronously on the cached configuration.
     */
    RefreshResult forceRefresh();

    /**
     * Initiates a force refresh asynchronously on the cached configuration.
     *
     * @return the future which executes the refresh.
     */
    CompletableFuture<RefreshResult> forceRefreshAsync();

    /**
     * Sets the default user.
     *
     * @param user the default user.
     */
    void setDefaultUser(User user);

    /**
     * Sets the default user to null.
     */
    void clearDefaultUser();

    /**
     * Configures the SDK to allow HTTP requests.
     */
    void setOnline();

    /**
     * Configures the SDK to not initiate HTTP requests and work only from its cache.
     */
    void setOffline();

    /**
     * Indicates whether the SDK is in offline mode or not.
     *
     * @return true when the SDK is configured not to initiate HTTP requests, otherwise false.
     */
    boolean isOffline();

    /**
     * Access to hooks for event subscription.
     *
     * @return the hooks object used for event subscription.
     */
    ConfigCatHooks getHooks();
}
