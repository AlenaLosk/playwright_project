# Памятка по Playwright (Java)

Шпаргалка по библиотеке [Playwright for Java](https://playwright.dev/java/) — контекст проекта: JUnit 5 + OWNER + Maven. Версия в проекте: `com.microsoft.playwright:playwright:1.64.0`.

---

## 1. Подключение

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.64.0</version>
    <scope>test</scope>
</dependency>
```

Браузеры Playwright скачивает сам при первом запуске (в `%USERPROFILE%\AppData\Local\ms-playwright`). Явная установка/обновление:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Полезные ключи CLI: `install --with-deps`, `uninstall`, `--dry-run`, `install chromium --only-shell` (headless-оболочка, меньше весит).

---

## 2. Канонический скелет JUnit 5

`Playwright`/`Browser` — один на весь класс, `BrowserContext`/`Page` — свежие на каждый тест. Быстро, изолированно, без состояний-«хвостов».

```java
public class UiTest {
    static Playwright playwright;
    static Browser browser;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext();
        context.setDefaultTimeout(10_000);   // мс; дефолт 30 c
        page = context.newPage();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close(); // закрывает и page внутри контекста
    }
}
```

⚠️ **Не создавайте `Page` в обход контекста** (`browser.newPage()` внутри теста) — контекст из `@BeforeEach` останется неиспользуемым, а страница не закроется вместе с ним.

Другие браузеры: `playwright.chromium()`, `.firefox()`, `.webkit()`, `.chromium().launch(new LaunchOptions().setChannel("chrome"))` — системный Chrome/Edge.

---

## 3. Локаторы

| Способ | Пример | Когда |
|---|---|---|
| CSS | `page.locator("#k_email")` | быстро, но хрупко |
| Роль (рекомендуется) | `page.getByRole(AriaRole.BUTTON, new ByRoleOptions().setName("Войти"))` | устойчиво к вёрстке |
| Текст | `page.getByText("Войти")` | ссылки, подписи |
| Placeholder | `page.getByPlaceholder("Email")` | поля ввода |
| Label | `page.getByLabel("Пароль")` | поля с `<label>` |
| Test id | `page.getByTestId("submit-btn")` | если тесты проставляют `data-testid` |
| XPath | `page.locator("xpath=//button[@type='submit']")` | последнее средство |

Уточнение и цепочки:

```java
page.locator("tbody tr").filter(new LocatorFilterOptions().hasText("Sonne")).first();
page.locator(".card").getByRole(AriaRole.BUTTON);
locator.first() / .last() / .nth(2);
locator.count();
```

⚠️ **Strict mode**: если локатор нашёл больше одного элемента — действие упадёт с ошибкой. Это фича: она ловит кривые локаторы сразу, а не «кликает в рандомный элемент».

---

## 4. Действия

```java
page.navigate("https://pokemonbattle.ru/login");
page.goBack();  page.reload();

Locator loginField = page.locator("#k_email");
loginField.fill("user@mail.com");      // ВВОД: заменяет значение целиком, сам очищает поле
loginField.press("Enter");             // одиночные клавиши: "Enter", "Control+a", "Tab"
loginField.click();                    // в т.ч. click(new ClickOptions().setModifiers(...))

page.locator("#k_password").fill("secret");
page.locator("button[type='submit']").click();

// чекбоксы/радио — НЕ click(), а:
page.locator("#agree").check();
page.locator("#spam").uncheck();

// селект:
page.locator("#city").selectOption("msk");            // по value
page.locator("#city").selectOption(new SelectOption().setLabel("Москва"));

locator.hover();
locator.focus();
locator.dragAndTo(page.locator("#trash"));
locator.setInputFiles(Paths.get("upload.png"));
```

⚠️ **Один сабмит на форму**: либо `click()` по кнопке, либо `press("Enter")` в поле — не всё сразу, форма отправится раньше, чем вы «заполните остальное».

`fill()` vs устаревшее поведение: `fill` — «программный» ввод значения; для «живого» набора по клавишам есть `locator.pressSequentially("text")`. В 99% случаев нужен `fill`.

---

## 5. Ожидания

Playwright **автоожидает** перед каждым действием: элемент существует → виден → стабилен → доступен для событий. Никаких `Thread.sleep`!

Явные ожидания — когда нужно именно подождать:

```java
page.waitForURL(url -> url.toString().startsWith("https://pokemonbattle.ru"));
page.waitForURL("**/battle/**");                        // glob
page.waitForLoadState(LoadState.NETWORKIDLE);           // «сеть затихла»
page.locator(".header_card_trainer_id_num").waitFor();  // элемент появился и виден
page.waitForResponse(resp -> resp.url().contains("/api/login") && resp.status() == 200);
page.waitForPopup(() -> page.locator("#open").click()); // попап после действия
```

⚠️ `locator.isVisible()` **не ждёт** — возвращает состояние «прямо сейчас» (для проверок — смотрите ассерты ниже). `page.waitForTimeout(ms)` — это «легальный sleep», почти всегда признак, что надо ждать конкретное событие.

---

## 6. Ассерты

Ретраящие ассерты Playwright (пакет `com.microsoft.playwright.assertions`) сами ждут выполнения условия — их и используйте для проверок состояния UI:

```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

assertThat(page.locator("#k_email")).isVisible();
assertThat(page.locator(".header_card_trainer_id_num")).hasText("260");
assertThat(page.locator(".card")).hasCount(3);
assertThat(page.locator("#k_email")).hasValue("user@mail.com");
assertThat(page.locator("#save")).isEnabled();
assertThat(page.locator("#save")).hasAttribute("type", "submit");
assertThat(page.locator(".card").first()).containsText("Sonne");
```

JUnit-ассерты (`assertEquals`, `assertTrue`) — для данных вне UI (строки, числа, конфиг).

Как читать значения (не для проверок):

```java
String text  = locator.textContent();   // сырой текст, включая скрытый; может быть null
String text2 = locator.innerText();     // видимый текст
String value = locator.inputValue();    // значение input/textarea/select — НЕ getAttribute("value")!
```

⚠️ `getAttribute("value")` читает **атрибут** из DOM, а не текущее значение поля. После `fill()` атрибут не меняется — для input есть `inputValue()`, для сравнения — `hasValue()`.

---

## 7. Чтение значений, скриншоты, трассировка (отладка)

```java
// скриншот
page.screenshot(new Page.ScreenshotOptions()
        .setPath(Paths.get("target", "screen.png"))
        .setFullPage(true));

// слоу-мо и видимый браузер — смотреть «вживую»
browser = playwright.chromium().launch(
        new LaunchOptions().setHeadless(false).setSlowMo(500.0));

// брейкпоинт в UI-инспекторе Playwright Inspector
page.pause();                                    // внутри теста
// или запуск с переменной окружения PWDEBUG=1
```

Трейс — «чёрный ящик» упавшего теста (скриншоты шагов, сеть, консоль):

```java
BrowserContext context = browser.newContext();
context.tracing().start(new TracingOptions().setScreenshots(true).setSnapshots(true));
// ... тест ...
context.tracing().stop(new TracingStopOptions().setPath(Paths.get("target", "trace.zip")));
// посмотреть: mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/trace.zip"
```

Логи страницы: `page.onConsoleMessage(msg -> ...);`, `page.onRequest(...)` / `page.onResponse(...)`, `page.onPageError(...)`.

---

## 8. Изоляция и состояние

- `BrowserContext` = инкогнито-вкладка со своими cookies/localStorage. Один тест — один контекст.
- Сохранить/восстановить логин (не логиниться в каждом тесте):

```java
context.storageState(new BrowserContext.StorageStateOptions()
        .setPath(Paths.get("target", "auth.json")));          // сохранить

browser.newContext(new Browser.NewContextOptions()
        .setStorageStatePath(Paths.get("target", "auth.json"))); // восстановить
```

- Несколько вкладок: `Page popup = context.waitForPopup(() -> ...)`, `context.pages()`.
- iframe: `page.frameLocator("#widget").getByRole(AriaRole.BUTTON)`.
- Моки/перехват сети: `page.route("**/api/**", route -> route.fulfill(new Route.FulfillOptions().setStatus(200).setBody("...")));`

---

## 9. Грабли, на которые наступают чаще всего

1. **Теневание переменной `page`** в тесте локальной `Page page = browser.newPage()` — setup из `@BeforeEach` мёртв, страница не закрывается. Проверяйте, что работаете с полем.
2. **`isVisible()` не ждёт** — на медленной странице даст ложный fail. Для проверок — `assertThat(locator).isVisible()`, для ожидания — `locator.waitFor()`.
3. **`getAttribute("value")` вместо `inputValue()`** — атрибут ≠ текущее значение.
4. **`Thread.sleep` после клика** — вместо этого `waitForURL`, `waitForResponse`, ретраящий ассерт.
5. **Двойной/тройной сабмит** (Enter + Enter + click) — форма уходит после первого, остальные шаги работают «в пустоту» или на перезагруженной странице.
6. **`clear()` перед `fill()`** — лишний: `fill` сам заменяет значение.
7. **Один `Page` на несколько тестов** — состояние (куки, localStorage) перетекает между тестами. Нужен `BrowserContext` на тест.
8. **Локатор матчит 2+ элементов** — strict mode уронит действие. Уточняйте: `.first()`, `.filter(...)`, `getByRole(...).setName(...)`.
9. **NPE в teardown**: если падает `@BeforeEach`, `context` ещё `null` — ставьте guard `if (context != null)`.
10. **Не закрыт `Playwright`/`Browser`** в `@AfterAll` — процесс браузера остаётся висеть после сборки.

---

## 10. Конфигурация через OWNER (как в этом проекте)

`Playwright`/`Browser` поднимаются в `@BeforeAll` из конфига:

```java
@Key("headless") @DefaultValue("true") boolean headless();
@Key("timeout.seconds") int timeoutSeconds();
```

```java
browser = playwright.chromium().launch(new LaunchOptions().setHeadless(cfg.headless()));
...
context.setDefaultTimeout(cfg.timeoutSeconds() * 1000.0);
```

Переопределение без правки файла: `mvn test -Dheadless=false` (OWNER читает системные свойства поверх `ProjectConfig.properties`).

---

## 11. Шпаргалка «Selenium → Playwright»

| Selenium | Playwright |
|---|---|
| `driver.get(url)` | `page.navigate(url)` |
| `driver.findElement(by)` | `page.locator(css)` / `getByRole(...)` |
| `sendKeys(text)` | `fill(text)` |
| `element.clear(); sendKeys(t)` | `fill(t)` |
| `click()` | `click()` (автоожидание) |
| `WebDriverWait.until(...)` | не нужен / `waitFor`, `waitForURL`, ретраящие ассерты |
| `element.getText()` | `textContent()` / `innerText()` |
| `element.getAttribute("value")` | `inputValue()` |
| `isSelected()` | `isChecked()` |
| `Select.selectByValue(v)` | `selectOption(v)` |
| `driver.quit()` | `context.close()` / `browser.close()` / `playwright.close()` |
| `JavascriptExecutor` | `locator.evaluate("el => el.value")`, `page.evaluate(...)` |

---

Ссылки: [playwright.dev/java](https://playwright.dev/java/) · [Locators](https://playwright.dev/java/docs/locators) · [Assertions](https://playwright.dev/java/docs/test-assertions) · [Trace Viewer](https://playwright.dev/java/docs/trace-viewer)
