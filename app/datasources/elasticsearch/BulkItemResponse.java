package datasources.elasticsearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BulkItemResponse {
    private final ActionWriteResponse response;
    private int index;
    private String opType;


    public BulkItemResponse(int index, String opType, ActionWriteResponse response) {
        this.index = index;
        this.opType = opType;
        this.response = response;
    }

    @JsonCreator
    public BulkItemResponse(@JsonProperty("create") IndexResponse indexResponse) {
        this(0, "create",indexResponse);
    }

    boolean isFailed() {
        return response.getError() != null;
    }

    public String getIndex() {
        return response.getIndex();
    }
}
