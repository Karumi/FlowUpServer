package datasources.database;

import com.avaje.ebean.ExpressionList;
import models.AllowedUUID;
import models.ApiKey;
import org.joda.time.DateTime;
import utils.Time;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

public class ApiKeyDatasource {

    private final Time time;

    @Inject
    public ApiKeyDatasource(Time time) {
        this.time = time;
    }

    public ApiKey findByApiKeyValue(String apiKeyValue) {
        return ApiKey.find.fetch("organization").where().eq("value", apiKeyValue).findUnique();
    }

    public ApiKey create() {
        String value = UUID.randomUUID().toString().replaceAll("-", "");
        return create(value);
    }

    public ApiKey create(String value) {
        return create(value, true);
    }

    public ApiKey create(String value, boolean enabled) {
        ApiKey apiKey = new ApiKey();
        apiKey.setValue(value);
        apiKey.setEnabled(enabled);
        apiKey.save();
        return apiKey;
    }

    public boolean delete(String apiKeyValue) {
        ApiKey apiKey = findByApiKeyValue(apiKeyValue);
        return apiKey != null && apiKey.delete();
    }

    public void addAllowedUUID(ApiKey apiKey, String uuid) {
        AllowedUUID allowedUUID = new AllowedUUID();
        long nowTimestamp = this.time.now().toDate().getTime();
        allowedUUID.setCreatedAt(new Timestamp(nowTimestamp));
        allowedUUID.setInstallationUUID(uuid);
        allowedUUID.setApiKey(apiKey);
        allowedUUID.save();
    }

    public int getTodayAllowedUUIDsCount(ApiKey apiKey) {
        return getTodayAllowedUUIDQuery(apiKey).findRowCount();
    }

    public Set<AllowedUUID> getTodayAllowedUUIDs(ApiKey apiKey) {
        return getTodayAllowedUUIDQuery(apiKey).findSet();
    }

    public void deleteAllowedUUIDs(ApiKey apiKey) {
        DateTime yesterday = time.getYesterdayMidnightDate();
        DateTime today = time.getTodayMidnightDate();
        ExpressionList<AllowedUUID> uuidsToDelete = AllowedUUID.find.where().eq("api_key_id", apiKey.getId()).between("created_at", yesterday, today);
        uuidsToDelete.delete();
    }

    private ExpressionList<AllowedUUID> getTodayAllowedUUIDQuery(ApiKey apiKey) {
        DateTime today = time.getTodayMidnightDate();
        DateTime tomorrow = time.getTomorrowMidhtDate();
        return AllowedUUID.find.where().eq("api_key_id", apiKey.getId()).between("created_at", today, tomorrow);
    }
}
