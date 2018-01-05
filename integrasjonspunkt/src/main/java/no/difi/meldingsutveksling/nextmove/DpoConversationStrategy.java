package no.difi.meldingsutveksling.nextmove;

import no.difi.meldingsutveksling.ServiceIdentifier;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.noarkexchange.MessageContextException;
import no.difi.meldingsutveksling.noarkexchange.MessageSender;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static no.difi.meldingsutveksling.ServiceIdentifier.DPO;
import static no.difi.meldingsutveksling.nextmove.logging.ConversationResourceMarkers.markerFrom;

@Component
public class DpoConversationStrategy implements ConversationStrategy {

    private static final Logger log = LoggerFactory.getLogger(DpoConversationStrategy.class);

    private ServiceRegistryLookup sr;
    private MessageSender messageSender;

    @Autowired
    DpoConversationStrategy(ServiceRegistryLookup sr,
                            MessageSender messageSender) {
        this.sr = sr;
        this.messageSender = messageSender;
    }

    @Override
    public ResponseEntity send(ConversationResource cr) {
        List<ServiceRecord> serviceRecords = sr.getServiceRecords(cr.getReceiverId());
        Optional<ServiceRecord> serviceRecord = serviceRecords.stream()
                .filter(r -> DPO == r.getServiceIdentifier())
                .findFirst();
        if (!serviceRecord.isPresent()) {
            List<ServiceIdentifier> acceptableTypes = serviceRecords.stream()
                    .map(ServiceRecord::getServiceIdentifier)
                    .collect(Collectors.toList());
            String errorStr = String.format("Message is of type '%s', but receiver '%s' accepts types '%s'.",
                    DPO, cr.getReceiverId(), acceptableTypes);
            log.error(markerFrom(cr), errorStr);
            return ResponseEntity.badRequest().body(ErrorResponse.builder().error("serviceIdentifier_not_acceptable")
                    .errorDescription(errorStr).build());
        }
        try {
            messageSender.sendMessage(cr);
        } catch (MessageContextException e) {
            log.error("Send message failed.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during sending. Check logs");
        }
        Audit.info(String.format("Message [id=%s, serviceIdentifier=%s] sent to altinn",
                cr.getConversationId(), cr.getServiceIdentifier()),
                markerFrom(cr));

        return ResponseEntity.ok().build();
    }

}
