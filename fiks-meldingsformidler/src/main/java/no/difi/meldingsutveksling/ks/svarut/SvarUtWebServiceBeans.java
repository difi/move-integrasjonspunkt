package no.difi.meldingsutveksling.ks.svarut;

import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.axiom.AxiomSoapMessageFactory;
import org.springframework.ws.transport.http.AbstractHttpWebServiceMessageSender;

import java.lang.invoke.MethodHandles;

@Configuration
@ConditionalOnProperty(name = "difi.move.feature.enableDPF", havingValue = "true")
@EnableConfigurationProperties({IntegrasjonspunktProperties.class})
public class SvarUtWebServiceBeans {
    private Logger logger = org.slf4j.LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setMtomEnabled(true);
        marshaller.setContextPath(Forsendelse.class.getPackage().getName());
        marshaller.setValidationEventHandler(event -> {
            logger.error(event.getMessage(), event.getLinkedException());
            return false;
        });
        return marshaller;
    }

    @Bean
    public AxiomSoapMessageFactory svarUtMessageFactory() {
        final AxiomSoapMessageFactory axiomSoapMessageFactory = new AxiomSoapMessageFactory();
        axiomSoapMessageFactory.setSoapVersion(SoapVersion.SOAP_12);
        return axiomSoapMessageFactory;
    }

    @Bean
    public AbstractHttpWebServiceMessageSender svarUtMessageSender(IntegrasjonspunktProperties properties) {
        return new PreauthMessageSender(
                properties.getFiks().getUt().getUsername(),
                properties.getFiks().getUt().getPassword());
    }

    @Bean
    public SvarUtWebServiceClientImpl svarUtClient(Jaxb2Marshaller marshaller, AxiomSoapMessageFactory svarUtMessageFactory, AbstractHttpWebServiceMessageSender svarUtMessageSender, SvarUtFaultInterceptor interceptor) {
        SvarUtWebServiceClientImpl client = new SvarUtWebServiceClientImpl();
        client.setDefaultUri("http://localhost:8080");
        client.setMarshaller(marshaller);
        client.setUnmarshaller(marshaller);

        client.setMessageFactory(svarUtMessageFactory);
        client.setMessageSender(svarUtMessageSender);

        final ClientInterceptor[] interceptors = new ClientInterceptor[1];
        interceptors[0] = interceptor;
        client.setInterceptors(interceptors);

        return client;
    }
}
