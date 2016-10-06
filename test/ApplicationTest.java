import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.ws.WSResponse;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.WithApplication;
import usecases.MetricsDatasource;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.inject.Bindings.bind;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;

/**
 *
 * Simple (JUnit) tests that can call all parts of a play app.
 * If you are interested in mocking a whole application, see the wiki for more details.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationTest extends WithApplication {

    @Mock
    private MetricsDatasource metricsDatasource;

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .overrides(bind(MetricsDatasource.class).toInstance(metricsDatasource))
                .build();
    }

    @Test
    public void testReportAPI() {
        RequestBuilder requestBuilder = fakeRequest("POST", "/report")
            .bodyText(getFile("reportRequest.json"))
            .header("Content-Type", "application/json");
        when(metricsDatasource.writeFakeCounter()).thenReturn(CompletableFuture.completedFuture(mock(WSResponse.class)));

        Result result = route(requestBuilder);

        assertEquals("{\"message\":\"Metrics Inserted\"}", contentAsString(result));
        assertEquals(OK, result.status());
        assertEquals("application/json", result.contentType().get());
        assertEquals("UTF-8", result.charset().get());
    }

    private String getFile(String fileName){

        String result = "";

        ClassLoader classLoader = getClass().getClassLoader();
        try {
            result = IOUtils.toString(classLoader.getResourceAsStream(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;

    }
}
