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

    public boolean isFetched() {
        return this.status == Status.FETCHED;
    }

    public boolean isNotModified() {
        return this.status == Status.NOT_MODIFIED;
    }

    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public Entry entry() {
        return this.entry;
    }

    public String error() { return this.error; }

    FetchResponse(Status status, Entry entry, String error) {
        this.status = status;
        this.entry = entry;
        this.error = error;
    }

    public static FetchResponse fetched(Entry entry) {
        return new FetchResponse(Status.FETCHED, entry, null);
    }

    public static FetchResponse notModified() {
        return new FetchResponse(Status.NOT_MODIFIED, Entry.empty, null);
    }

    public static FetchResponse failed(String error) {
        return new FetchResponse(Status.FAILED, Entry.empty, error);
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
                Config config = entry.config;
                if (config.preferences == null) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                String newUrl = config.preferences.baseUrl;
                if (newUrl.equals(this.url)) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                int redirect = config.preferences.redirect;

                // we have a custom url set, and we didn't get a forced redirect
                if (this.urlIsCustom && redirect != RedirectMode.FORCE_REDIRECT.ordinal()) {
                    return CompletableFuture.completedFuture(fetchResponse);
                }

                this.url = newUrl;

                if (redirect == RedirectMode.NO_REDIRECT.ordinal()) { // no redirect
                    return CompletableFuture.completedFuture(fetchResponse);
                } else { // redirect
                    if (redirect == RedirectMode.SHOULD_REDIRECT.ordinal()) {
                        this.logger.warn("Your builder.dataGovernance() parameter at ConfigCatClient " +
                                "initialization is not in sync with your preferences on the ConfigCat " +
                                "Dashboard: https://app.configcat.com/organization/data-governance. " +
                                "Only Organization Admins can access this preference.");
                    }

                    if (executionCount > 0) {
                        return this.executeFetchAsync(executionCount - 1, entry.eTag);
                    }
                }

            } catch (Exception exception) {
                this.logger.error("Exception in ConfigFetcher.executeFetchAsync", exception);
                return CompletableFuture.completedFuture(fetchResponse);
            }

            this.logger.error("Redirect loop during config.json fetch. Please contact support@configcat.com.");
            return CompletableFuture.completedFuture(fetchResponse);
        });
    }

    private CompletableFuture<FetchResponse> getResponseAsync(String eTag) {
        Request request = this.getRequest(eTag);
        CompletableFuture<FetchResponse> future = new CompletableFuture<>();
        this.httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                String generalMessage = "An error occurred during fetching the latest config.json.";
                if (!closed.get()) {
                    if (e instanceof SocketTimeoutException) {
                        String message = "Request timed out. Timeout values: [connect: " + httpClient.connectTimeoutMillis() + "ms, read: " + httpClient.readTimeoutMillis() + "ms, write: " + httpClient.writeTimeoutMillis() + "ms]";
                        logger.error(message, e);
                        future.complete(FetchResponse.failed(message));
                        return;
                    }
                    logger.error(generalMessage, e);
                }
                future.complete(FetchResponse.failed(generalMessage));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String content = body.string();
                        String eTag = response.header("ETag");
                        Result<Config> result = deserializeConfig(content);
                        if (result.error() != null) {
                            future.complete(FetchResponse.failed(result.error()));
                            return;
                        }
                        logger.debug("Fetch was successful: new config fetched.");
                        future.complete(FetchResponse.fetched(new Entry(result.value(), eTag, System.currentTimeMillis())));
                    } else if (response.code() == 304) {
                        logger.debug("Fetch was successful: config not modified.");
                        future.complete(FetchResponse.notModified());
                    } else {
                        String message = "Double-check your SDK Key at https://app.configcat.com/sdkkey. Received unexpected response: " + response.code();
                        logger.error(message);
                        future.complete(FetchResponse.failed(message));
                    }
                } catch (SocketTimeoutException e) {
                    String message = "Request timed out. Timeout values: [connect: " + httpClient.connectTimeoutMillis() + "ms, read: " + httpClient.readTimeoutMillis() + "ms, write: " + httpClient.writeTimeoutMillis() + "ms]";
                    logger.error(message, e);
                    future.complete(FetchResponse.failed(message));
                } catch (Exception e) {
                    String message = "Exception in ConfigFetcher.getResponseAsync";
                    logger.error(message, e);
                    future.complete(FetchResponse.failed(message + ": " + e.getMessage()));
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
            String message = "JSON parsing failed. " + e.getMessage();
            this.logger.error(message);
            return Result.error(message, null);
        }
    }
}

