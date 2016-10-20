package datasources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IndexResponse extends ActionWriteResponse {
    private final String id;
    private final String type;
    private final long version;
    private final boolean created;

    public IndexResponse(String index, String type, String id, long version, boolean created) {
        super(index);
        this.type = type;
        this.id = id;
        this.version = version;
        this.created = created;
    }

    @JsonCreator
    public IndexResponse(@JsonProperty("_index") String index, @JsonProperty("_type") String type,
                         @JsonProperty("_id") String id, @JsonProperty("_version") long version,
                         @JsonProperty("_shards") IndexResponse.ShardInfo shardInfo, @JsonProperty("status") String status) {
        super(index);
        this.type = type;
        this.id = id;
        this.version = version;
        this.created = true;
        this.shardInfo = shardInfo;
    }

}
