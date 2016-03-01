package no.difi.meldingsutveksling.transport.altinn;

import no.difi.meldingsutveksling.domain.sbdh.Document;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocumentHeader;
import no.difi.meldingsutveksling.elma.ELMALookup;
import no.difi.meldingsutveksling.transport.Transport;
import no.difi.meldingsutveksling.transport.TransportFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class MultiTransportFactory implements TransportFactory {

    @Autowired


    @Autowired
    ELMALookup elmaLookup;

    @Override
    public Transport createTransport(Document message) {
        StandardBusinessDocumentHeader standardBusinessDocumentHeader = message.getStandardBusinessDocumentHeader();
        final String receiverOrganisationNumber = standardBusinessDocumentHeader.getReceiverOrganisationNumber();
        return new AltinnTransport(receiverOrganisationNumber, elmaLookup);
    }

}
