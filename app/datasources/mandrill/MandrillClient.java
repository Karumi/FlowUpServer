package datasources.mandrill;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import play.Configuration;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class MandrillClient {

    public static final String MESSAGES_SEND_TEMPLATE_ENDPOINT = "/api/1.0/messages/send-template.json";

    private final WSClient ws;
    private final String baseUrl;
    private final String apiKey;

    @Inject
    public MandrillClient(WSClient ws, @Named("mandrill") Configuration configuration) {
        this.ws = ws;

        String scheme = configuration.getString("scheme");
        String host = configuration.getString("host");
        String port = configuration.getString("port");
        this.baseUrl = scheme + "://" + host + ":" + port;

        this.apiKey = configuration.getString("api_key");
    }

    public CompletionStage<MessagesSendTemplateResponse> sendMessageWithTemplate(String templateName, Message message) {
        ObjectNode payload = Json.newObject();
        payload.put("key", this.apiKey)
                .put("template_name", templateName);
        payload.set("template_content", Json.newArray().add(Json.newObject()));// Required By API
        payload.set("message", Json.toJson(message));

        return this.ws.url(baseUrl + MESSAGES_SEND_TEMPLATE_ENDPOINT).post(payload).thenApply(response -> {
                    Logger.debug(response.getBody());
                    if (response.getStatus() != Http.Status.OK) {
                        return Json.fromJson(response.asJson(), MessagesSendTemplateResponse.class);
                    } else {
                        return new MessagesSendTemplateResponse("success", 200, "", "");
                    }
                }
        );
    }
}

@Data
class Message {
    private final String subject;
    private final String fromEmail;
    private final String fromName;
    private final Recipient to;
    private final Var[] globalMergeVars;
}

@Data
class Var {
    private final String name;
    private final String content;
}

@Data
class Recipient {
    private final String email;
    private final String name;
    private final String type;
}

@Data
class MessagesSendTemplateResponse {
    private final String status;
    private final int code;
    private final String name;
    private final String message;
}
