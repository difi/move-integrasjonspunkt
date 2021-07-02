package no.difi.meldingsutveksling.nextmove.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceBusPayloadConverter {

    private final ObjectMapper objectMapper;

    public ServiceBusPayload convert(String input) throws IOException {
        return convert(input.getBytes(StandardCharsets.UTF_8));
    }

    public ServiceBusPayload convert(byte[] input) throws IOException {
        return objectMapper.readValue(input, ServiceBusPayload.class);
    }
}
