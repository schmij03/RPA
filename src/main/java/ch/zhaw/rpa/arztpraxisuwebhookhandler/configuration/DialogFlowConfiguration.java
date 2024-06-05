package ch.zhaw.rpa.arztpraxisuwebhookhandler.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.google.api.client.json.gson.GsonFactory;

@Configuration
public class DialogFlowConfiguration {

    @Bean
    public GsonFactory gsonFactory() {
        return GsonFactory.getDefaultInstance();
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}