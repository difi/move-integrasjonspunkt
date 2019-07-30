package no.difi.meldingsutveksling.cucumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.java.After;
import cucumber.api.java.en.And;
import lombok.RequiredArgsConstructor;
import no.difi.meldingsutveksling.nextmove.ServiceBusRestTemplate;
import no.difi.meldingsutveksling.nextmove.servicebus.ServiceBusPayload;
import no.difi.meldingsutveksling.pipes.Plumber;
import org.apache.commons.io.IOUtils;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.PipedInputStream;
import java.net.URI;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;

@RequiredArgsConstructor
public class ServiceBusInSteps {

    private final ServiceBusRestTemplate serviceBusRestTemplate;
    private final Holder<Message> messageInHolder;
    private final ObjectMapper objectMapper;
    private final AsicFactory asicFactory;
    private final Plumber plumber;

    @After
    public void after() {
        messageInHolder.reset();
    }

    @And("^the ServiceBus has the message available$")
    public void theServiceBusHasTheMessageAvailable() throws IOException {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add("BrokerProperties", "{ \"MessageId\" : \"1\", \"LockToken\" : \"T1\", \"SequenceNumber\" : \"S1\" }");

        Message message = messageInHolder.get();

        PipedInputStream is = plumber.pipe("create asic", inlet -> asicFactory.createAsic(message, inlet)).outlet();
        byte[] asic = IOUtils.toByteArray(is);
        is.close();
        byte[] base64encodedAsic = Base64.getEncoder().encode(asic);
        ServiceBusPayload serviceBusPayload = ServiceBusPayload.of(message.getSbd(), base64encodedAsic);
        String body = objectMapper.writeValueAsString(serviceBusPayload);

        ResponseEntity<String> messageResponse = new ResponseEntity<>(body, headers, HttpStatus.OK);
        ResponseEntity<String> notFound = ResponseEntity.notFound().build();

        doAnswer(new Answer<ResponseEntity<String>>() {
            private int count = 0;

            @Override
            public ResponseEntity<String> answer(InvocationOnMock invocation) {
                URI uri = invocation.getArgument(0);
                if (uri.toString().endsWith("/head")) {
                    ++count;
                    return count == 1 ? messageResponse : notFound;
                }

                return ResponseEntity.ok("OK");
            }
        }).when(serviceBusRestTemplate)
                .exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(String.class));

    }
}