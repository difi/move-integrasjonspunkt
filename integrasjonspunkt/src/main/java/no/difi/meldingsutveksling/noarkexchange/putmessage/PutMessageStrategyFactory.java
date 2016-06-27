package no.difi.meldingsutveksling.noarkexchange.putmessage;

import com.sun.org.apache.xerces.internal.dom.ElementNSImpl;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.noarkexchange.MessageSender;

import static no.difi.meldingsutveksling.noarkexchange.PayloadUtil.isAppReceipt;

/**
 * Factory clsss for putmessage strategies. Responsible for inspecting a payload and returning an appropriate
 * Strategy implementation
 *
 * @author Glenn Bech
 */

public final class PutMessageStrategyFactory {


    public static final String MESSAGE_INDICATOR = "Melding";

    private MessageSender messageSender;

    private PutMessageStrategyFactory(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public static PutMessageStrategyFactory newInstance(MessageSender messageSender) {
        return new PutMessageStrategyFactory(messageSender);
    }

    public PutMessageStrategy create(Object payload) {
        if (isAppReceipt(payload)) {
            Audit.info("Messagetype AppReceipt");
            return new AppReceiptPutMessageStrategy(messageSender);
        }
        if (isEPhorte(payload)) {
            Audit.info("Messagetype EDU - CData");
            return new BestEDUPutMessageStrategy(messageSender);
        }
        if (isUnknown(payload)) {
            Audit.error("Unknown payload class");
            throw new MeldingsUtvekslingRuntimeException("unknown payload class " + payload);
        }
        if (isBestEDUMessage(payload)) {
            Audit.info("Messagetype EDU HtmlEndoced");
            return new BestEDUPutMessageStrategy(messageSender);
        }
        Audit.error("Unknown payload string");
        throw new MeldingsUtvekslingRuntimeException("Unknown String based payload " + payload);
    }

    private boolean isUnknown(Object payload) {
        return !(payload instanceof String);
    }

    private boolean isEPhorte(Object payload) {
        return payload instanceof ElementNSImpl;
    }

    private boolean isBestEDUMessage(Object payload) {
        return ((String) payload).contains(MESSAGE_INDICATOR);
    }
}
