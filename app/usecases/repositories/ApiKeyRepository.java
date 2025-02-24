package usecases.repositories;

import datasources.database.ApiKeyDatasource;
import models.AllowedUUID;
import models.ApiKey;
import models.Version;
import play.cache.CacheApi;
import utils.Time;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class ApiKeyRepository {

    private static final int API_KEY_CACHE_TTL = (int) TimeUnit.HOURS.toSeconds(1);
    private static final int API_KEY_TODAY_ALLOWED_UUID_COUNT_TTL = (int) TimeUnit.DAYS.toSeconds(1);
    private static final int ALLOWED_UUIDS_TTL = (int) TimeUnit.DAYS.toSeconds(1);
    private static final String API_KEY_CACHE_KEY = "apiKey.value.";
    private static final String TODAY_ALLOWED_UUID_COUNT_CACHE_KEY = "apiKey.todayAllowedUUIDCount.";
    private static final String TODAY_ALLOWED_UUIDS = "allowedUUIDs.";

    private final CacheApi cache;
    private final ApiKeyDatasource apiKeyDatasource;
    private final Time time;

    @Inject
    public ApiKeyRepository(CacheApi cache, ApiKeyDatasource apiKeyDatasource, Time time) {
        this.cache = cache;
        this.apiKeyDatasource = apiKeyDatasource;
        this.time = time;
    }

    @NotNull
    public ApiKey create() {
        ApiKey apiKey = apiKeyDatasource.create();
        flushApiKeyCache(apiKey);
        return apiKey;
    }

    @NotNull
    public ApiKey create(String value) {
        return create(value, true);
    }

    @NotNull
    public ApiKey create(String value, boolean enabled) {
        return create(value, enabled, null);
    }

    @NotNull
    public ApiKey create(String value, boolean enabled, Version minAndroidSdkSupportedVersion) {
        ApiKey apiKey = apiKeyDatasource.create(value, enabled, minAndroidSdkSupportedVersion);
        flushApiKeyCache(apiKey);
        return apiKey;
    }

    @Nullable
    public ApiKey getApiKey(String apiKey) {
        return cache.getOrElse(getApiKeyCacheKey(apiKey),
                () -> apiKeyDatasource.findByApiKeyValue(apiKey),
                API_KEY_CACHE_TTL);
    }

    public CompletionStage<ApiKey> getApiKeyAsync(String apiKey) {
        return CompletableFuture.supplyAsync(() -> getApiKey(apiKey));
    }

    public void addAllowedUUID(ApiKey apiKey, String uuid) {
        apiKeyDatasource.addAllowedUUID(apiKey, uuid);
        flushAllowedUUIDCache(apiKey);
    }

    public boolean containsAllowedUUID(ApiKey apiKey, String uuid) {
        Set<AllowedUUID> thisMonthAllowedUUIDS = getThisMonthAllowedUUIDS(apiKey);
        return thisMonthAllowedUUIDS.stream()
                .filter(allowedUUID ->
                        allowedUUID.getInstallationUUID().equals(uuid))
                .count() > 0;
    }

    public int getThisMonthAllowedUUIDCount(ApiKey apiKey) {
        return cache.getOrElse(getAllowedUUIDCountCacheKey(apiKey.getValue()),
                () -> apiKeyDatasource.getThisMonthAllowedUUIDsCount(apiKey),
                API_KEY_TODAY_ALLOWED_UUID_COUNT_TTL);
    }

    public Set<AllowedUUID> getThisMonthAllowedUUIDS(ApiKey apiKey) {
        return cache.getOrElse(getAllowedUUIDsCacheKey(apiKey.getValue()),
                () -> apiKeyDatasource.getThisMonthAllowedUUIDs(apiKey),
                ALLOWED_UUIDS_TTL);
    }

    public void deleteOldAllowedUUIDs() {
        apiKeyDatasource.deleteAllowedUUIDs();
    }

    private void flushApiKeyCache(ApiKey apiKey) {
        cache.remove(getApiKeyCacheKey(apiKey.getValue()));
    }

    private void flushAllowedUUIDCache(ApiKey apiKey) {
        flushAllowedUUIDCache(apiKey.getValue());
    }

    private void flushAllowedUUIDCache(String apiKey) {
        cache.remove(getAllowedUUIDCountCacheKey(apiKey));
        cache.remove(getAllowedUUIDsCacheKey(apiKey));
    }

    private String getApiKeyCacheKey(String apiKey) {
        return API_KEY_CACHE_KEY + apiKey;
    }

    private String getAllowedUUIDCountCacheKey(String apiKey) {
        return TODAY_ALLOWED_UUID_COUNT_CACHE_KEY + apiKey + "." + getNumericDay();
    }

    private String getAllowedUUIDsCacheKey(String apiKey) {
        return TODAY_ALLOWED_UUIDS + apiKey + "." + getNumericDay();
    }

    private int getNumericDay() {
        return time.getTodayNumericDay();
    }

}
