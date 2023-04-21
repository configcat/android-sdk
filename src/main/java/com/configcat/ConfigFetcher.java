package com.configcat;

import java9.util.concurrent.CompletableFuture;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

class FetchResponse {
    public enum Status {
        FETCHED,
        NOT_MODIFIED,
        FAILED
    }

    private final Status status;
    private final Entry entry;
    private final String error;
    private final boolean fetchTimeUpdatable;

    public boolean isFetched() {
        return this.status == Status.FETCHED;
    }

    public boolean isNotModified() {
        return this.status == Status.NOT_MODIFIED;
    }

    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public boolean isFetchTimeUpdatable() { return fetchTimeUpdatable; }

    public Entry entry() {
        return this.entry;
    }

    public String error() { return this.error; }

    FetchResponse(Status status, Entry entry, String error, boolean fetchTimeUpdatable) {
        this.status = status;
        this.entry = entry;
        this.error = error;
        this.fetchTimeUpdatable = fetchTimeUpdatable;
    }

    public static FetchResponse fetched(Entry entry) {
        return new FetchResponse(Status.FETCHED, entry == null ? Entry.EMPTY : entry, null, false);
    }

    public static FetchResponse notModified() {
        return new FetchResponse(Status.NOT_MODIFIED, Entry.EMPTY, null, true);
    }

    public static FetchResponse failed(String error, boolean fetchTimeUpdatable) {
        return new FetchResponse(Status.FAILED, Entry.EMPTY, error, fetchTimeUpdatable);
    }
}

class ConfigFetcher implements Closeable {
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ConfigCatLogger logger;
    private final OkHttpClient httpClient;
    private final String mode;
    private final String sdkKey;
    private final boolean urlIsCustom;

    private String url;

    enum RedirectMode {
        NO_REDIRECT,
        SHOULD_REDIRECT,
        FORCE_REDIRECT
    }

    ConfigFetcher(OkHttpClient httpClient,
                  ConfigCatLogger logger,
                  String sdkKey,
                  String url,
                  boolean urlIsCustom,
                  String pollingIdentifier) {
        this.logger = logger;
        this.sdkKey = sdkKey;
        this.urlIsCustom = urlIsCustom;
        this.url = url;
        this.httpClient = httpClient;
        this.mode = pollingIdentifier;
    }

    public CompletableFuture<FetchResponse> fetchAsync(String eTag) {
        return this.executeFetchAsync(2, eTag);
    }

    private CompletableFuture<FetchResponse> executeFetchAsync(int executionCount, String eTag) {
        return this.getResponseAsync(eTag).thenComposeAsync(fetchResponse -> {
            if (!fetchResponse.isFetched()) {
                return CompletableFuture.completedFuture(fetchResponse);
            }
            try {
                Entry entry = fetchResponse.entry();
                Config config = entry.getConfig();
                if (config.getPreferences() == null) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                String newUrl = config.getPreferences().getBaseUrl();
                if (newUrl.equals(this.url)) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                int redirect = config.getPreferences().getRedirect();

                // we have a custom url set, and we didn't get a forced redirect
                if (this.urlIsCustom && redirect != RedirectMode.FORCE_REDIRECT.ordinal()) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                this.url = newUrl;

                if (redirect == RedirectMode.NO_REDIRECT.ordinal()) { // no redirect
                    return CompletableFuture.completedFuture(fetchResponse);
                } else { // redirect
                    if (redirect == RedirectMode.SHOULD_REDIRECT.ordinal()) {
                        this.logger.warn(3002, ConfigCatLogMessages.DATA_GOVERNANCE_IS_OUT_OF_SYNC_WARN);
                    }

                    if (executionCount > 0) {
                        return this.executeFetchAsync(executionCount - 1, entry.getETag());
                    }
                }

            } catch (Exception exception) {
                this.logger.error(1103, ConfigCatLogMessages.FETCH_FAILED_DUE_TO_UNEXPECTED_ERROR, exception);
                return CompletableFuture.completedFuture(fetchResponse);
            }

            this.logger.error(1104, ConfigCatLogMessages.FETCH_FAILED_DUE_TO_REDIRECT_LOOP_ERROR);
            return CompletableFuture.completedFuture(fetchResponse);
        });
    }

    private CompletableFuture<FetchResponse> getResponseAsync(String eTag) {
        Request request = this.getRequest(eTag);
        CompletableFuture<FetchResponse> future = new CompletableFuture<>();
        this.httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String generalMessage = ConfigCatLogMessages.FETCH_FAILED_DUE_TO_UNEXPECTED_ERROR;
                if (!closed.get()) {
                    if (e instanceof SocketTimeoutException) {
                        String message = ConfigCatLogMessages.getFetchFailedDueToRequestTimeout(httpClient.connectTimeoutMillis() ,httpClient.readTimeoutMillis() ,httpClient.writeTimeoutMillis());
                        logger.error(1102, message, e);
                        future.complete(FetchResponse.failed(message, false));
                        return;
                    }
                    logger.error(1103, generalMessage, e);
                }
                future.complete(FetchResponse.failed(generalMessage, false));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String content = body.string();
                        String eTag = response.header("ETag");
                        Result<Config> result = deserializeConfig(content);
                        if (result.error() != null) {
                            future.complete(FetchResponse.failed(result.error(), false));
                            return;
                        }
                        logger.debug("Fetch was successful: new config fetched.");
                        future.complete(FetchResponse.fetched(new Entry(result.value(), eTag, System.currentTimeMillis())));
                    } else if (response.code() == 304) {
                        logger.debug("Fetch was successful: config not modified.");
                        future.complete(FetchResponse.notModified());
                    } else if (response.code() == 403 || response.code() == 404) {
                        String message = ConfigCatLogMessages.FETCH_FAILED_DUE_TO_INVALID_SDK_KEY_ERROR;
                        logger.error(1100, message);
                        future.complete(FetchResponse.failed(message, true));
                    } else {
                        String message = ConfigCatLogMessages.getFetchFailedDueToUnexpectedHttpResponse(response.code(),response.message());
                        logger.error(1101, message);
                        future.complete(FetchResponse.failed(message, false));
                    }
                } catch (SocketTimeoutException e) {
                    String message = ConfigCatLogMessages.getFetchFailedDueToRequestTimeout(httpClient.connectTimeoutMillis() ,httpClient.readTimeoutMillis() ,httpClient.writeTimeoutMillis());
                    logger.error(1102, message, e);
                    future.complete(FetchResponse.failed(message, false));
                } catch (Exception e) {
                    String message = ConfigCatLogMessages.FETCH_FAILED_DUE_TO_UNEXPECTED_ERROR;
                    logger.error(1103, message, e);
                    future.complete(FetchResponse.failed(message + " " + e.getMessage() , false));
                }
            }
        });
        return future;
    }

    @Override
    public void close() throws IOException {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        if (this.httpClient != null) {
            this.httpClient.dispatcher().executorService().shutdownNow();
            this.httpClient.connectionPool().evictAll();
            Cache cache = this.httpClient.cache();
            if (cache != null)
                cache.close();
        }
    }

    private Request getRequest(String eTag) {
        String requestUrl = this.url + "/configuration-files/" + this.sdkKey + "/" + Constants.CONFIG_JSON_NAME + ".json";
        Request.Builder builder = new Request.Builder()
                .addHeader("X-ConfigCat-UserAgent", "ConfigCat-Droid/" + this.mode + "-" + Constants.VERSION);

        if (eTag != null && !eTag.isEmpty())
            builder.addHeader("If-None-Match", eTag);

        return builder.url(requestUrl).build();
    }

    private Result<Config> deserializeConfig(String json) {
        try {
            return Result.success(Utils.gson.fromJson(json, Config.class));
        } catch (Exception e) {
            String message = ConfigCatLogMessages.FETCH_RECEIVED_200_WITH_INVALID_BODY_ERROR;
            this.logger.error(1105, message, e);
            return Result.error(message, null);
        }
    }
}

