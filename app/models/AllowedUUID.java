package models;

import com.avaje.ebean.Model;
import com.avaje.ebean.annotation.CreatedTimestamp;
import play.data.validation.Constraints;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
public class AllowedUUID extends Model implements Serializable {

    private static final long serialVersionUID = 1L;

    public static Finder<UUID, AllowedUUID> find = new Finder<>(AllowedUUID.class);

    @Id
    private UUID id;
    private String installationUUID;
    @CreatedTimestamp
    private Timestamp createdAt;
    @Constraints.Required
    @ManyToOne
    private ApiKey apiKey;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInstallationUUID() {
        return installationUUID;
    }

    public void setInstallationUUID(String installationUUID) {
        this.installationUUID = installationUUID;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

}
