package no.difi.meldingsutveksling.nextmove;

import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.dpi.MeldingsformidlerClient;
import no.difi.meldingsutveksling.dpi.MeldingsformidlerException;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.KeystoreProvider;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import static no.difi.meldingsutveksling.nextmove.logging.ConversationResourceMarkers.markerFrom;

@Component
public class DpiConversationStrategy implements ConversationStrategy {

    private IntegrasjonspunktProperties props;
    private ServiceRegistryLookup sr;
    private KeystoreProvider keystore;
    private NextMoveUtils nextMoveUtils;

    @Autowired
    DpiConversationStrategy(IntegrasjonspunktProperties props,
                            ServiceRegistryLookup sr,
                            NextMoveUtils nextMoveUtils,
                            KeystoreProvider keystore) {
        this.props = props;
        this.sr = sr;
        this.nextMoveUtils = nextMoveUtils;
        this.keystore = keystore;
    }

    @Override
    public ResponseEntity send(ConversationResource conversationResource) {

        DpiConversationResource cr = (DpiConversationResource) conversationResource;
        ServiceRecord serviceRecord = sr.getServiceRecord(cr.getReceiverId());

        NextMoveDpiRequest request = new NextMoveDpiRequest(props, nextMoveUtils, cr, serviceRecord);
        MeldingsformidlerClient client = new MeldingsformidlerClient(props.getDpi(), keystore.getKeyStore());
        try {
            client.sendMelding(request);
        } catch (MeldingsformidlerException e) {
            Audit.error("Failed to send message to DPI", markerFrom(cr), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message to DPI");
        }

        return ResponseEntity.ok().build();
    }
}
