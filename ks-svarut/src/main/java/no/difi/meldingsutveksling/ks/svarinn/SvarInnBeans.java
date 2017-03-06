package no.difi.meldingsutveksling.ks.svarinn;

import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name="difi.move.fiks.enabled", havingValue = "true")
@EnableConfigurationProperties(IntegrasjonspunktProperties.class)
public class SvarInnBeans {
    @Bean
    public RestTemplate svarInnRestTemplate(IntegrasjonspunktProperties properties) {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        builder = builder.rootUri(properties.getFiks().getInn().getBaseUrl());
        builder = builder.basicAuthorization(properties.getFiks().getInn().getUsername(), properties.getFiks().getInn().getPassword());
        return builder.build();
    }

    @Bean
    public SvarInnClient svarInnClient(RestTemplate svarInnRestTemplate) {
        return new SvarInnClient(svarInnRestTemplate);
    }

}
