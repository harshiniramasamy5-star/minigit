package in.nitw.minigit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling   // recycle bin daily cleanup (U11)
public class MiniGitApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniGitApplication.class, args);
    }
}
