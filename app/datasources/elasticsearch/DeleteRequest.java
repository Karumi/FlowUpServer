package datasources.elasticsearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

public class DeleteRequest {

    @JsonProperty("delete")
    private final DeleteRequest.Metadata indexToDelete;

    @Data
    static class Metadata implements Serializable {

        @JsonProperty("_index")
        private final String index;
        @JsonProperty("_type")
        private final String type;
        @JsonProperty("_id")
        private final String id;
    }

    DeleteRequest(String index, String type, String id) {
        this.indexToDelete = new Metadata(index, type, id);
    }
}
