import org.aeonbits.owner.Config;

public interface ProjectConfig extends Config {
    @Key("base.url")
    String baseUrl();

    @Key("timeout.seconds")
    int timeoutSeconds();

    @Key("headless")
    @DefaultValue("false")
    boolean headless();

    String login();

    String password();

    @Key("trainer.id")
    int trainerId();
}
