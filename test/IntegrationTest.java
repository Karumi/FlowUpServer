import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;
import play.test.WithBrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class IntegrationTest extends WithBrowser {

    @Test
    public void testLogin() {
        browser.goTo("/login");
        assertNotNull(browser.$("div#login a"));
        assertEquals("Sign in with Google", (browser.$("div#login a img").getAttribute("alt")));
    }
}
