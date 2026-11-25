import com.microsoft.playwright.*;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UIAuthorizationTest {
    private static final String LOGIN_FIELD = "#k_email";
    private static final String PASSWORD_FIELD = "#k_password";
    private static final String SUBMIT_BUTTON = "button[type='submit']";
    private static final String TRAINER_ID_IN_HEADER = ".header_card_trainer_id_num";

    private static Playwright playwright;
    private static Browser browser;
    private static ProjectConfig cfg;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void initAll() {
        cfg = ConfigFactory.create(ProjectConfig.class);
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(cfg.headless()));
    }

    @AfterAll
    static void destroyAll() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext();
        context.setDefaultTimeout(cfg.timeoutSeconds() * 1000.0);
        page = context.newPage();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void authorizationTest() {
        page.navigate(cfg.baseUrl() + "/login");

        Locator loginField = page.locator(LOGIN_FIELD);
        Locator passwordField = page.locator(PASSWORD_FIELD);
        Locator submitButton = page.locator(SUBMIT_BUTTON);

        assertThat(loginField).isVisible();
        assertThat(passwordField).isVisible();
        assertThat(submitButton).isVisible();

        loginField.fill(cfg.login());
        passwordField.fill(cfg.password());
        submitButton.click();

        page.waitForURL(url -> url.toString().startsWith(cfg.baseUrl()));

        String trainerId = page.locator(TRAINER_ID_IN_HEADER).textContent();
        assertEquals(String.valueOf(cfg.trainerId()),
                trainerId == null ? null : trainerId.trim(),
                "ID тренера не совпадает с ожидаемым");
    }
}
