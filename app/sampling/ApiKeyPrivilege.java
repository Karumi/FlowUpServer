package sampling;

import models.ApiKey;
import models.Platform;
import models.Version;
import usecases.repositories.ApiKeyRepository;

import javax.inject.Inject;

public class ApiKeyPrivilege {

    private final ApiKeyRepository apiKeyRepository;

    @Inject
    public ApiKeyPrivilege(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public boolean isAllowed(String apiKeyValue, String uuid, Version version) {
        ApiKey apiKey = apiKeyRepository.getApiKey(apiKeyValue);
        if (apiKey == null) {
            return false;
        }
        // The Android SDK 0.1.3 or younger versions does not
        // send the UUID as part of the header. This is
        // because the sampling was implemented in client side.
        if (uuid == null) {
            return true;
        }
        return hasPrivilege(apiKey, uuid, version);
    }

    private boolean hasPrivilege(ApiKey apiKey, String uuid, Version version) {
        if (!apiKey.isEnabled()) {
            return false;
        }
        Version minAndroidApiKeyVersionSupported = Version.fromString(apiKey.getMinAndroidSDKSupported());
        if (version.getPlatform() == Platform.ANDROID && !isVersionSupported(version, minAndroidApiKeyVersionSupported)) {
            return false;
        }
        if (version.getPlatform() == Platform.UNKNOWN) {
            return false;
        }
        if (version.isDebugVersion()) {
            return true;
        }
        if (hasExceededTheNumberOfAllowedUUIDs(apiKey)) {
            return apiKeyRepository.containsAllowedUUID(apiKey, uuid);
        } else {
            if (!apiKeyRepository.containsAllowedUUID(apiKey, uuid)) {
                apiKeyRepository.addAllowedUUID(apiKey, uuid);
            }
            return true;
        }
    }

    private boolean isVersionSupported(Version version, Version minApiKeyVersionSupported) {
        return minApiKeyVersionSupported.compareTo(version) <= 0;
    }

    private boolean hasExceededTheNumberOfAllowedUUIDs(ApiKey apiKey) {
        int allowedUUIDCount = apiKeyRepository.getThisMonthAllowedUUIDCount(apiKey);
        return allowedUUIDCount >= apiKey.getNumberOfAllowedUUIDs();
    }
}
