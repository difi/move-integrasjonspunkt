package no.difi.meldingsutveksling.noarkexchange;


import com.thoughtworks.xstream.XStream;
import no.difi.asic.SignatureHelper;
import no.difi.meldingsutveksling.adresseregister.client.CertificateNotFoundException;
import no.difi.meldingsutveksling.domain.*;
import no.difi.meldingsutveksling.domain.sbdh.Scope;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.eventlog.Event;
import no.difi.meldingsutveksling.eventlog.EventLog;
import no.difi.meldingsutveksling.noarkexchange.schema.*;
import no.difi.meldingsutveksling.oxalisexchange.IntegrasjonspunktNokkel;
import no.difi.meldingsutveksling.services.AdresseregisterService;
import no.difi.meldingsutveksling.transport.Transport;
import no.difi.meldingsutveksling.transport.TransportFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static no.difi.meldingsutveksling.noarkexchange.StandardBusinessDocumentFactory.create;

@Component
public class MessageSender {

    private static final String JP_ID = "jpId";
    private static final String DATA = "data";

    @Autowired
    EventLog eventLog;

    @Autowired
    @Qualifier("multiTransport")
    TransportFactory transportFactory;

    @Autowired
    AdresseregisterService adresseregister;


    boolean setSender(IntegrasjonspunktContext context, AddressType sender) {
        if (sender == null) {
            return false;
        }
        Avsender avsender;
        Certificate sertifikat;
        try {
            sertifikat = adresseregister.getCertificate(sender.getOrgnr());
        } catch (CertificateNotFoundException e) {
            eventLog.log(new Event().setExceptionMessage(e.toString()));
            return false;
        }
        avsender = Avsender.builder(new Organisasjonsnummer(sender.getOrgnr()), new Noekkelpar(findPrivateKey(), sertifikat)).build();
        context.setAvsender(avsender);
        return true;
    }

    boolean setRecipient(IntegrasjonspunktContext context, AddressType receiver) {
        if (receiver == null) {
            return false;
        }
        X509Certificate receiverCertificate;
        try {
            receiverCertificate = (X509Certificate) adresseregister.getCertificate(receiver.getOrgnr());

        } catch (CertificateNotFoundException e) {
            eventLog.log(new Event().setExceptionMessage(e.toString()));
            return false;
        }
        Mottaker mottaker = Mottaker.builder(new Organisasjonsnummer(receiver.getOrgnr()), receiverCertificate).build();
        context.setMottaker(mottaker);
        return true;
    }

    public PutMessageResponseType sendMessage(PutMessageRequestType message) {

        String conversationId = message.getEnvelope().getConversationId();
        String journalPostId = getJpId(message);

        IntegrasjonspunktContext context = new IntegrasjonspunktContext();
        context.setJpId(journalPostId);

        EnvelopeType envelope = message.getEnvelope();
        if (envelope == null) {
            return createErrorResponse("Missing envelope");
        }

        AddressType receiver = message.getEnvelope().getReceiver();
        if (!setRecipient(context, receiver)) {
            return createErrorResponse("invalid recipient, no recipient or missing certificate for " + receiver.getOrgnr());
        }

        AddressType sender = message.getEnvelope().getSender();
        if (!setSender(context, sender)) {
            return createErrorResponse("invalid sender, no sender or missing certificate for " + receiver.getOrgnr());
        }
        eventLog.log(new Event(ProcessState.SIGNATURE_VALIDATED));

        SignatureHelper helper = new IntegrasjonspunktNokkel().getSignatureHelper();
        StandardBusinessDocument sbd ;
        try {
            sbd = create(message, helper, context.getAvsender(), context.getMottaker());

        } catch (IOException e) {
            eventLog.log(new Event().setJpId(journalPostId).setArkiveConversationId(conversationId).setProcessStates(ProcessState.MESSAGE_SEND_FAIL));
            return createErrorResponse("IO Error on Asic-e or sbd creation " + e.getMessage() + ", see log.");

        }
        Scope item = sbd.getStandardBusinessDocumentHeader().getBusinessScope().getScope().get(0);
        String hubCid = item.getInstanceIdentifier();
        eventLog.log(new Event().setJpId(journalPostId).setArkiveConversationId(conversationId).setHubConversationId(hubCid).setProcessStates(ProcessState.CONVERSATION_ID_LOGGED));

        Transport t = transportFactory.createTransport(sbd);
        t.send(sbd);

        eventLog.log(createOkStateEvent(message));
        return new PutMessageResponseType();
    }

    private String getJpId(PutMessageRequestType message) {
        Document document = getDocument(message);
        NodeList messageElement = document.getElementsByTagName(JP_ID);
        if (messageElement.getLength() == 0) {
            throw new MeldingsUtvekslingRuntimeException("no " + JP_ID + " element in document ");
        }
        return messageElement.item(0).getTextContent();
    }

    private Document getDocument(PutMessageRequestType message) throws MeldingsUtvekslingRuntimeException {
        DocumentBuilder documentBuilder = getDocumentBuilder();
        Element element = (Element) message.getPayload();
        NodeList nodeList = element.getElementsByTagName(DATA);
        if (nodeList.getLength() == 0) {
            throw new MeldingsUtvekslingRuntimeException("no " + DATA + " element in payload");
        }
        Node payloadData = nodeList.item(0);
        String payloadDataTextContent = payloadData.getTextContent();
        Document document;

        try {
            document = documentBuilder.parse(new InputSource(new ByteArrayInputStream(payloadDataTextContent.getBytes("utf-8"))));
        } catch (SAXException | IOException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }
        return document;
    }

    private DocumentBuilder getDocumentBuilder() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }
        return builder;
    }


    private PutMessageResponseType createErrorResponse(String message) {
        PutMessageResponseType response = new PutMessageResponseType();
        AppReceiptType receipt = new AppReceiptType();
        receipt.setType(message);
        response.setResult(receipt);
        return response;
    }


    //todo refactor
    PrivateKey findPrivateKey() {
        IntegrasjonspunktNokkel nokkel = new IntegrasjonspunktNokkel();
        return nokkel.loadPrivateKey();
    }

    public void setAdresseregister(AdresseregisterService adresseregister) {
        this.adresseregister = adresseregister;
    }

    public void setEventLog(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    private Event createErrorEvent(PutMessageRequestType anyOject, Exception e) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setExceptionMessage(event.getMessage());
        event.setProcessStates(ProcessState.MESSAGE_SEND_FAIL);
        event.setMessage(xs.toXML(anyOject));
        return event;
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

}

