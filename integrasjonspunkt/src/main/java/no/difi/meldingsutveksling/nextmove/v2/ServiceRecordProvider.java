package no.difi.meldingsutveksling.nextmove.v2;

import lombok.RequiredArgsConstructor;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.exceptions.MissingMessageTypeException;
import no.difi.meldingsutveksling.exceptions.ReceiverDoesNotAcceptProcessException;
import no.difi.meldingsutveksling.nextmove.BusinessMessage;
import no.difi.meldingsutveksling.serviceregistry.SRParameter;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookupException;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceRecordProvider {

    private final ServiceRegistryLookup serviceRegistryLookup;

    ServiceRecord getServiceRecord(StandardBusinessDocument sbd) {
        return sbd.getBusinessMessage(BusinessMessage.class)
                .map(p -> getServiceRecord(sbd, p))
                .orElseThrow(MissingMessageTypeException::new);
    }

    private ServiceRecord getServiceRecord(StandardBusinessDocument sbd, BusinessMessage<?> businessMessage) {
        try {
            SRParameter.SRParameterBuilder parameterBuilder = SRParameter.builder(sbd.getReceiverIdentifier())
                    .process(sbd.getProcess());

            sbd.getOptionalConversationId().ifPresent(parameterBuilder::conversationId);

            if (businessMessage.getSikkerhetsnivaa() != null) {
                parameterBuilder.securityLevel(businessMessage.getSikkerhetsnivaa());
            }
            return serviceRegistryLookup.getServiceRecord(
                    parameterBuilder.build(),
                    sbd.getDocumentType());
        } catch (ServiceRegistryLookupException e) {
            throw new ReceiverDoesNotAcceptProcessException(sbd.getProcess(), e.getLocalizedMessage());
        }
    }
}
