package no.difi.meldingsutveksling.noarkexchange;


import com.thoughtworks.xstream.XStream;
import no.difi.meldingsutveksling.IntegrasjonspunktNokkel;
import no.difi.meldingsutveksling.config.IntegrasjonspunktConfig;
import no.difi.meldingsutveksling.domain.Avsender;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.domain.Mottaker;
import no.difi.meldingsutveksling.domain.Noekkelpar;
import no.difi.meldingsutveksling.domain.Organisasjonsnummer;
import no.difi.meldingsutveksling.domain.ProcessState;
import no.difi.meldingsutveksling.domain.sbdh.Scope;
import no.difi.meldingsutveksling.eventlog.Event;
import no.difi.meldingsutveksling.eventlog.EventLog;
import no.difi.meldingsutveksling.noarkexchange.putmessage.ErrorStatus;
import no.difi.meldingsutveksling.noarkexchange.schema.PutMessageRequestType;
import no.difi.meldingsutveksling.noarkexchange.schema.PutMessageResponseType;
import no.difi.meldingsutveksling.services.AdresseregisterService;
import no.difi.meldingsutveksling.services.CertificateException;
import no.difi.meldingsutveksling.transport.Transport;
import no.difi.meldingsutveksling.transport.TransportFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static no.difi.meldingsutveksling.noarkexchange.PutMessageResponseFactory.createErrorResponse;
import static no.difi.meldingsutveksling.noarkexchange.PutMessageResponseFactory.createOkResponse;

@Component
public class MessageSender {

    @Autowired
    private EventLog eventLog;

    org.slf4j.Logger log = LoggerFactory.getLogger(MessageSender.class.getName());

    @Autowired
    @Qualifier("multiTransport")
    private TransportFactory transportFactory;

    @Autowired
    private AdresseregisterService adresseregister;

    @Autowired
    private IntegrasjonspunktConfig configuration;

    @Autowired
    private IntegrasjonspunktNokkel keyInfo;

    @Autowired
    private StandardBusinessDocumentFactory standardBusinessDocumentFactory;

    Avsender createAvsender(PutMessageRequestAdapter message) throws AvsenderException {
        if (!message.hasSenderPartyNumber()) {
            message.setSenderPartyNumber(configuration.getOrganisationNumber());
        }

        Certificate certificate;
        try {
            certificate = adresseregister.getCertificate(message.getSenderPartynumber());
        } catch (CertificateException e) {
            throw new AvsenderException(e);
        }
        PrivateKey privatNoekkel = keyInfo.loadPrivateKey();
        Avsender avsender = Avsender.builder(new Organisasjonsnummer(message.getSenderPartynumber()), new Noekkelpar(privatNoekkel, certificate)).build();

        return avsender;
    }

    private Mottaker createMottaker(String orgnr) throws MottakerException {
        X509Certificate receiverCertificate;
        try {
            receiverCertificate = (X509Certificate) adresseregister.getCertificate(orgnr);
        } catch(CertificateException e) {
            throw new MottakerException(e);
        }
        Mottaker mottaker = Mottaker.builder(new Organisasjonsnummer(orgnr), receiverCertificate).build();

        return mottaker;
    }

    public PutMessageResponseType sendMessage(PutMessageRequestType messageRequest) {
        PutMessageRequestAdapter message = new PutMessageRequestAdapter(messageRequest);

        MessageContext messageContext = createMessageContext(message);
        if(messageContext.hasErrors()) {
            return createErrorResponse(messageContext.getErrors().iterator().next());
        }

        eventLog.log(new Event(ProcessState.SIGNATURE_VALIDATED));

        no.difi.meldingsutveksling.domain.sbdh.Document sbd;
        try {
            sbd = standardBusinessDocumentFactory.create(messageRequest, messageContext.getAvsender(), messageContext.getMottaker());

        } catch (IOException e) {
            eventLog.log(new Event().setJpId(messageContext.getJournalPostId()).setArkiveConversationId(message.getConversationId()).setProcessStates(ProcessState.MESSAGE_SEND_FAIL));
            log.error("IO Error on Asic-e or sbd creation " + e.getMessage());
            return createErrorResponse(ErrorStatus.MISSING_SENDER);

        }
        Scope item = sbd.getStandardBusinessDocumentHeader().getBusinessScope().getScope().get(0);
        String hubCid = item.getInstanceIdentifier();
        eventLog.log(new Event().setJpId(messageContext.getJournalPostId()).setArkiveConversationId(message.getConversationId()).setHubConversationId(hubCid).setProcessStates(ProcessState.CONVERSATION_ID_LOGGED));

        Transport t = transportFactory.createTransport(sbd);
        t.send(configuration.getConfiguration(), sbd);

        eventLog.log(createOkStateEvent(messageRequest));

        return createOkResponse();
    }

    /**
     * Creates MessageContext to contain data needed to send a message such as
     * sender/recipient party numbers and certificates
     *
     * The context also contains error statuses if the message request has validation errors.
     *
     * @param message
     * @return
     */
    private MessageContext createMessageContext(PutMessageRequestAdapter message) {
        MessageContext context = new MessageContext();
        if(!message.hasRecieverPartyNumber()) {
            log.error(ErrorStatus.MISSING_RECIPIENT.toString());
            context.addError(ErrorStatus.MISSING_RECIPIENT);
        }
        if (!message.hasSenderPartyNumber() && !configuration.hasOrganisationNumber()) {
            throw new MeldingsUtvekslingRuntimeException();
        }

        JournalpostId p = JournalpostId.fromPutMessage(message);
        String journalPostId = p.value();

        context.setJpId(journalPostId);

        try {
            context.setMottaker(createMottaker(message.getRecieverPartyNumber()));
        } catch (MottakerException e) {
            log.error(ErrorStatus.CANNOT_RECIEVE + message.getRecieverPartyNumber() + e.toString());
            context.addError(ErrorStatus.CANNOT_RECIEVE);
        }

        try {
            context.setAvsender(createAvsender(message));
        } catch (AvsenderException e) {
            log.error(ErrorStatus.MISSING_SENDER + e.toString());
            context.addError(ErrorStatus.MISSING_SENDER);
        }

        return context;
    }

    public void setAdresseregister(AdresseregisterService adresseregister) {
        this.adresseregister = adresseregister;
    }

    public void setEventLog(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    public IntegrasjonspunktConfig getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IntegrasjonspunktConfig configuration) {
        this.configuration = configuration;
    }

    public IntegrasjonspunktNokkel getKeyInfo() {
        return keyInfo;
    }

    public void setKeyInfo(IntegrasjonspunktNokkel keyInfo) {
        this.keyInfo = keyInfo;
    }

    public void setTransportFactory(TransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    private Event createOkStateEvent(PutMessageRequestType anyOject) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setMessage(xs.toXML(anyOject));
        event.setProcessStates(ProcessState.SBD_SENT);
        return event;
    }

    public void setStandardBusinessDocumentFactory(StandardBusinessDocumentFactory standardBusinessDocumentFactory) {
        this.standardBusinessDocumentFactory = standardBusinessDocumentFactory;
    }

    public StandardBusinessDocumentFactory getStandardBusinessDocumentFactory() {
        return standardBusinessDocumentFactory;
    }

}

