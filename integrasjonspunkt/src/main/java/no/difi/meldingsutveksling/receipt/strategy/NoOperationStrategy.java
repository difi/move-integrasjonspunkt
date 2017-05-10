package no.difi.meldingsutveksling.receipt.strategy;

import no.difi.meldingsutveksling.ServiceIdentifier;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.receipt.Conversation;
import no.difi.meldingsutveksling.receipt.StatusStrategy;

import static no.difi.meldingsutveksling.receipt.ConversationMarker.markerFrom;

public class NoOperationStrategy implements StatusStrategy {

    @Override
    public void checkStatus(Conversation conversation) {
        Audit.info("Trying to check a receipt that is not handled by receipt strategy", markerFrom(conversation));
    }

    @Override
    public ServiceIdentifier getServiceIdentifier() {
        return ServiceIdentifier.UNKNOWN;
    }
}
