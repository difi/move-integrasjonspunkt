package no.difi.meldingsutveksling.nextmove;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.dokumentpakking.xml.Payload;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.domain.sbdh.EduDocument;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.xml.transform.StringSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Arrays.asList;

@Component
@Slf4j
public class ServiceBusRestClient {

    private static final String NEXTMOVE_QUEUE_PREFIX = "nextbestqueue";
    private static final String AUTH_HEADER = "Authorization";

    private ServiceRegistryLookup sr;
    private IntegrasjonspunktProperties props;
    private String localQueuePath;
    private JAXBContext jaxbContext;

    public ServiceBusRestClient(ServiceRegistryLookup sr,
                                IntegrasjonspunktProperties props) throws JAXBException {
        this.sr = sr;
        this.props = props;

        this.localQueuePath = NEXTMOVE_QUEUE_PREFIX+
                props.getOrg().getNumber()+
                props.getNextmove().getServiceBus().getMode();
        this.jaxbContext = JAXBContextFactory.createContext(new Class[]{EduDocument.class, Payload.class, ConversationResource.class}, null);
    }

    public void sendMessage(byte[] message, String queuePath) {
        String resourceUri = format("https://%s.servicebus.windows.net/%s/messages",
                props.getNextmove().getServiceBus().getNamespace(), queuePath);
        URI uri = convertToUri(resourceUri);

        String auth = createAuthorizationHeader(resourceUri);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTH_HEADER, auth);
        headers.add("BrokerProperties", "{}");
        HttpEntity<Object> httpEntity = new HttpEntity<>(message, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("{} got response {}, error sending message to service bus", resourceUri, response.getStatusCode().toString());
        }
    }

    public Optional<ServiceBusMessage> receiveMessage() {
        String resourceUri = format("https://%s.servicebus.windows.net/%s/messages/head",
                props.getNextmove().getServiceBus().getNamespace(),
                localQueuePath);

        String auth = createAuthorizationHeader(resourceUri);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTH_HEADER, auth);
        HttpEntity<Object> httpEntity = new HttpEntity<>(headers);

        URI uri = convertToUri(resourceUri);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, String.class);
        if (!asList(HttpStatus.OK, HttpStatus.CREATED).contains(response.getStatusCode())) {
            log.debug("{} got response {}, returning empty", resourceUri, response.getStatusCode().toString());
            return Optional.empty();
        }

        ServiceBusMessage.ServiceBusMessageBuilder sbmBuilder = ServiceBusMessage.builder();
        String brokerPropertiesJson = response.getHeaders().getFirst("BrokerProperties");
        JsonParser jsonParser = new JsonParser();
        JsonObject brokerProperties = jsonParser.parse(brokerPropertiesJson).getAsJsonObject();
        sbmBuilder.lockToken(brokerProperties.get("LockToken").getAsString())
                .messageId(brokerProperties.get("MessageId").getAsString())
                .sequenceNumber(brokerProperties.get("SequenceNumber").getAsString());

        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            EduDocument eduDocument = unmarshaller.unmarshal(new StringSource(response.getBody()), EduDocument.class).getValue();
            sbmBuilder.body(eduDocument);
            log.debug(format("Received message on queue=%s with conversationId=%s", localQueuePath, eduDocument.getConversationId()));
        } catch (JAXBException e) {
            log.error(String.format("Error unmarshalling service bus message with id=%s", brokerProperties.get("MessageId")), e);
        }

        return Optional.of(sbmBuilder.build());
    }

    public void deleteMessage(ServiceBusMessage message) {
        String resourceUri = format("https://%s.servicebus.windows.net/%s/messages/%s/%s",
                props.getNextmove().getServiceBus().getNamespace(),
                localQueuePath,
                message.getMessageId(),
                message.getLockToken());

        String auth = createAuthorizationHeader(resourceUri);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTH_HEADER, auth);
        HttpEntity<Object> httpEntity = new HttpEntity<>(headers);

        URI uri = convertToUri(resourceUri);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.DELETE, httpEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("{} got response {}, message [conversationId={}] was not deleted",
                    resourceUri, response.getStatusCode().toString(),
                    message.getBody().getConversationId());
        }
    }

    private URI convertToUri(String uriString) {
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            throw new MeldingsUtvekslingRuntimeException(format("Uri syntax error in: %s", uriString), e);
        }
    }

    private String createAuthorizationHeader(String resourceUri) {

        String urlEncoded;
        try {
            urlEncoded = URLEncoder.encode(resourceUri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }

        int expiry = Math.round(Instant.now().plusSeconds(20).toEpochMilli() / 1000f);
        String hashInput = urlEncoded+"\n"+expiry;

        byte[] bytes = Hashing.hmacSha256(getSasKey().getBytes(StandardCharsets.UTF_8))
                .hashBytes(hashInput.getBytes(StandardCharsets.UTF_8)).asBytes();
        byte[] signEncoded = Base64.getEncoder().encode(bytes);
        String signature;
        try {
            signature = URLEncoder.encode(new String(signEncoded), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }

        return format("SharedAccessSignature sr=%s&sig=%s&se=%s&skn=%s",
                urlEncoded,
                signature,
                expiry,
                props.getNextmove().getServiceBus().getSasKeyName());
    }

    public String getLocalQueuePath() {
        return this.localQueuePath;
    }

    public String getSasKey() {
        if (props.getOidc().isEnable()) {
            return sr.getSasToken();
        } else {
            return props.getNextmove().getServiceBus().getSasToken();
        }
    }
}
