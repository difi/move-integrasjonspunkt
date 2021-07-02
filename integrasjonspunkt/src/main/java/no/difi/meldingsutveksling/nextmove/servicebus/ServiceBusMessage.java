package no.difi.meldingsutveksling.nextmove.servicebus;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
@Builder
public class ServiceBusMessage {

    ServiceBusPayload payload;
    String lockToken;
    String messageId;
    String sequenceNumber;
}
