package no.difi.meldingsutveksling.config;

import no.difi.meldingsutveksling.validation.Asserter;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.validation.ClockProvider;
import javax.validation.Validation;
import javax.validation.Validator;
import java.time.Clock;


@Configuration
public class ValidationConfig {

    @Bean
    public ClockProvider clockProvider(Clock clock) {
        return () -> clock;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Bean
    @Primary
    public Validator validator(ClockProvider clockProvider) {
        Validation.byProvider(HibernateValidator.class);

        return new LocalValidatorFactoryBean() {
            @Override
            protected void postProcessConfiguration(javax.validation.Configuration<?> configuration) {
                if (configuration instanceof HibernateValidatorConfiguration) {
                    HibernateValidatorConfiguration config = (HibernateValidatorConfiguration) configuration;
                    config.clockProvider(clockProvider);
                }
            }
        };
    }

    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor(Validator validator) {
        MethodValidationPostProcessor mvpp = new MethodValidationPostProcessor();
        mvpp.setValidator(validator);
        return mvpp;
    }

    @Bean
    public Asserter asserter(Validator validator) {
        return new Asserter(validator);
    }
}
