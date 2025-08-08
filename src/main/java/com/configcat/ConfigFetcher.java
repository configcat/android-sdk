package com.configcat;

import java9.util.concurrent.CompletableFuture;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

class FetchResponse {
    public enum Status {
        FETCHED,
        NOT_MODIFIED,
        FAILED
    }

    private final Status status;
    private final Entry entry;
    private final Object error;
    private final boolean fetchTimeUpdatable;
    private final String cfRayId;

    public boolean isFetched() {
        return this.status == Status.FETCHED;
    }

    public boolean isNotModified() {
        return this.status == Status.NOT_MODIFIED;
    }

    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public boolean isFetchTimeUpdatable() {
        return fetchTimeUpdatable;
    }

    public Entry entry() {
        return this.entry;
    }

    public Object error() {
        return this.error;
    }

    public String cfRayId() {return this.cfRayId;}

    FetchResponse(Status status, Entry entry, Object error, boolean fetchTimeUpdatable, String cfRayId) {
        this.status = status;
        this.entry = entry;
        this.error = error;
        this.fetchTimeUpdatable = fetchTimeUpdatable;
        this.cfRayId = cfRayId;
    }

    public static FetchResponse fetched(Entry entry, String cfRayId) {
        return new FetchResponse(Status.FETCHED, entry == null ? Entry.EMPTY : entry, null, false, cfRayId);
    }

    public static FetchResponse notModified(String cfRayId) {
        return new FetchResponse(Status.NOT_MODIFIED, Entry.EMPTY, null, true, cfRayId);
    }

    public static FetchResponse failed(Object error, boolean fetchTimeUpdatable, String cfRayId) {
        return new FetchResponse(Status.FAILED, Entry.EMPTY, error, fetchTimeUpdatable, cfRayId);
    }
}

class ConfigFetcher implements Closeable {
    private final ConfigCatClient.HttpOptions httpOptions;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService executorService;
    private final ConfigCatLogger logger;
    private final String mode;
    private final String sdkKey;
    private final boolean urlIsCustom;

    private String url;

    enum RedirectMode {
        NO_REDIRECT,
        SHOULD_REDIRECT,
        FORCE_REDIRECT
    }

    ConfigFetcher(ConfigCatClient.HttpOptions httpOptions,
                  ConfigCatLogger logger,
                  String sdkKey,
                  String url,
                  boolean urlIsCustom,
                  String pollingIdentifier) {
        this.logger = logger;
        this.sdkKey = sdkKey;
        this.urlIsCustom = urlIsCustom;
        this.url = url;
        this.mode = pollingIdentifier;
        this.httpOptions = httpOptions;
        this.executorService = Executors.newCachedThreadPool();
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

            this.logger.error(1104, ConfigCatLogMessages.getFetchFailedDueToRedirectLoop(fetchResponse.cfRayId()));
            return CompletableFuture.completedFuture(fetchResponse);
        });
    }

    private CompletableFuture<FetchResponse> getResponseAsync(String eTag) {
        CompletableFuture<FetchResponse> future = new CompletableFuture<>();
        this.executorService.execute(() -> this.callHTTP(eTag, future));
        return future;
    }

    private void callHTTP(String previousETag, CompletableFuture<FetchResponse> result) {
        String requestUrl = this.url + "/configuration-files/" + this.sdkKey + "/" + Constants.CONFIG_JSON_NAME;
        HttpURLConnection urlConnection = null;

        try {
            URL fetchUrl = new URL(requestUrl);
            urlConnection = (HttpURLConnection) fetchUrl.openConnection();
            urlConnection.setConnectTimeout(httpOptions.getConnectTimeoutMillis());
            urlConnection.setReadTimeout(httpOptions.getReadTimeoutMillis());
            urlConnection.setUseCaches(false);
            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("X-ConfigCat-UserAgent", "ConfigCat-Droid/" + this.mode + "-" + Constants.VERSION);
            urlConnection.setDoOutput(true);

            if (previousETag != null && !previousETag.isEmpty())
                urlConnection.setRequestProperty("If-None-Match", previousETag);

            int responseCode = urlConnection.getResponseCode();
            Map<String, List<String>> responseHeaders = urlConnection.getHeaderFields();

            String cfRayId = readHeaderValue(responseHeaders, "CF-RAY");
            if (responseCode == 200) {
                String content = readBody(urlConnection.getInputStream());
                String eTag = readHeaderValue(responseHeaders,"ETag");
                Result<Config> configResult = deserializeConfig(content, cfRayId);
                if (configResult.error() != null) {
                    result.complete(FetchResponse.failed(configResult.error(), false, cfRayId));
                    return;
                }
                logger.debug("Fetch was successful: new config fetched.");
                result.complete(FetchResponse.fetched(new Entry(configResult.value(), eTag, content, System.currentTimeMillis()), cfRayId));
            } else if (responseCode == 304) {
                if(cfRayId != null) {
                    logger.debug(String.format("Fetch was successful: config not modified. %s", ConfigCatLogMessages.getCFRayIdPostFix(cfRayId)));
                } else {
                    logger.debug("Fetch was successful: config not modified.");
                }
                result.complete(FetchResponse.notModified(cfRayId));
            } else if (responseCode == 403 || responseCode == 404) {
                FormattableLogMessage message = ConfigCatLogMessages.getFetchFailedDueToInvalidSDKKey(cfRayId);
                logger.error(1100, message);
                result.complete(FetchResponse.failed(message, true, cfRayId));
            } else {
                FormattableLogMessage message = ConfigCatLogMessages.getFetchFailedDueToUnexpectedHttpResponse(responseCode, urlConnection.getResponseMessage(), cfRayId);
                logger.error(1101, message);
                result.complete(FetchResponse.failed(message, false, cfRayId));
            }

        } catch (SocketTimeoutException e) {
            FormattableLogMessage message = ConfigCatLogMessages.getFetchFailedDueToRequestTimeout(httpOptions.getConnectTimeoutMillis(), httpOptions.getReadTimeoutMillis());
            logger.error(1102, message, e);
            result.complete(FetchResponse.failed(message, false, null));
        } catch (Exception e) {
            String message = ConfigCatLogMessages.FETCH_FAILED_DUE_TO_UNEXPECTED_ERROR;
            logger.error(1103, message, e);
            result.complete(FetchResponse.failed(message + " " + e.getMessage(), false, null));
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        if (this.executorService != null) {
            this.executorService.shutdownNow();
        }
    }

    private String readHeaderValue(Map<String, List<String>> responseHeaders, String headerName) {
        if (responseHeaders == null || responseHeaders.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
        }
        return null;
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        return body.toString();
    }

    private Result<Config> deserializeConfig(String json, String cfRayId) {
        try {
            return Result.success(Utils.deserializeConfig(json));
        } catch (Exception e) {
            FormattableLogMessage message = ConfigCatLogMessages.getFetchReceived200WithInvalidBodyError(cfRayId);
            this.logger.error(1105, message, e);
            return Result.error(message, null);
        }
    }
}

