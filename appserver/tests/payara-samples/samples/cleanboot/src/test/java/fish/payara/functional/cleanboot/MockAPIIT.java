package fish.payara.functional.cleanboot;

import com.microsoft.playwright.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import java.io.IOException;

import fish.payara.samples.PayaraArquillianTestRunner;
import fish.payara.samples.PayaraTestShrinkWrap;
import fish.payara.samples.NotMicroCompatible;
import java.nio.file.Paths;

import org.jboss.shrinkwrap.api.spec.WebArchive;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(PayaraArquillianTestRunner.class)
@NotMicroCompatible
public class MockApiIT {

    static private Page page;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebArchive archive = PayaraTestShrinkWrap.getWebArchive();
        return archive;
    }

    @BeforeClass
    static public void openPage() throws IOException {
        //Load the Admin Console
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch();
        
        page = browser.newPage();
        mockLogo();
        page.navigate("http://localhost:4848/");
        page.waitForSelector("table[role='presentation']", new Page.WaitForSelectorOptions().setTimeout(120000));
    }

    @AfterClass
    static public void closePage() {
        page.close();
    }
    
    static public void mockLogo(){
        // Intercept the route to the Payara logo
        page.route("*/**/images/masthead-product_name_open-new.png", route -> {
            route.fulfill(new Route.FulfillOptions()
                    .setPath(Paths.get("src/test/resources/wildfly_icons_one-color-logo.png")));
        });
    }

    @Test
    @RunAsClient
    public void openAdminConsole() {
        AdminPage.gotoHomepage(page);
        assertThat(page).hasTitle("Payara Server Console - Common Tasks");
        page.pause();
    }
}
