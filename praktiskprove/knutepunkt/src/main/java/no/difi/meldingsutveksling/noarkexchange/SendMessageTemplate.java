package no.difi.meldingsutveksling.noarkexchange;


import com.thoughtworks.xstream.XStream;
import no.difi.meldingsutveksling.dokumentpakking.Dokumentpakker;
import no.difi.meldingsutveksling.domain.Avsender;
import no.difi.meldingsutveksling.domain.BestEduMessage;
import no.difi.meldingsutveksling.domain.Mottaker;
import no.difi.meldingsutveksling.domain.Noekkelpar;
import no.difi.meldingsutveksling.domain.Organisasjonsnummer;
import no.difi.meldingsutveksling.domain.SBD;
import no.difi.meldingsutveksling.eventlog.Event;
import no.difi.meldingsutveksling.eventlog.EventLog;
import no.difi.meldingsutveksling.eventlog.ProcessState;
import no.difi.meldingsutveksling.noarkexchange.schema.AddressType;
import no.difi.meldingsutveksling.noarkexchange.schema.AppReceiptType;
import no.difi.meldingsutveksling.noarkexchange.schema.ObjectFactory;
import no.difi.meldingsutveksling.noarkexchange.schema.PutMessageRequestType;
import no.difi.meldingsutveksling.noarkexchange.schema.PutMessageResponseType;
import no.difi.meldingsutveksling.services.AdresseregisterMock;
import no.difi.meldingsutveksling.services.AdresseregisterService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.UUID;

@Component
public abstract class SendMessageTemplate {

    @Autowired
    EventLog eventLog;

    @Autowired
    AdresseregisterService adresseregister;

    Dokumentpakker dokumentpakker;

    public SendMessageTemplate(Dokumentpakker dokumentpakker, AdresseregisterService adresseregister) {
        this.dokumentpakker = dokumentpakker;
        this.adresseregister = adresseregister;
    }

    public SendMessageTemplate() {
        this(new Dokumentpakker(), new AdresseregisterMock());
    }

    SBD createSBD(PutMessageRequestType sender, KnutepunktContext context) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(PutMessageRequestType.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(new ObjectFactory().createPutMessageRequest(sender), os);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

        return new SBD(dokumentpakker.pakkDokumentISbd(new BestEduMessage(os.toByteArray()), context.getAvsender(), context.getMottaker(), UUID.randomUUID()
                .toString(), "BEST/EDU"));
    }

    abstract void sendSBD(SBD sbd) throws IOException;

    boolean verifySender(AddressType sender, KnutepunktContext context) {
        try {
            Avsender avsender = null;
            Certificate sertifikat = adresseregister.getCertificate(sender.getOrgnr());
            if (sertifikat == null)
                throw new InvalidSender();
            avsender = Avsender.builder(new Organisasjonsnummer(sender.getOrgnr()), new Noekkelpar(findPrivateKey(), sertifikat)).build();
            context.setAvsender(avsender);
        } catch (IllegalArgumentException e) {
            throw new InvalidSender();
        }
        return true;
    }

    boolean verifyRecipient(AddressType receiver, KnutepunktContext context) {
        try {
            PublicKey mottakerpublicKey = adresseregister.getPublicKey(receiver.getOrgnr());
            if (mottakerpublicKey == null)
                throw new InvalidReceiver();
            Mottaker mottaker = new Mottaker(new Organisasjonsnummer(receiver.getOrgnr()), mottakerpublicKey);
            context.setMottaker(mottaker);
        } catch (IllegalArgumentException e) {
            throw new InvalidReceiver();
        }
        return true;
    }

    public PutMessageResponseType sendMessage(PutMessageRequestType message) {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Element element = (Element) message.getPayload();
        NodeList dataElement = (NodeList) element.getElementsByTagName("com.sun.org.apache.xerces.internal.dom.CharacterDataImpl");
        NodeList nodeList = element.getElementsByTagName("data");
        Node payloadData = nodeList.item(0);
        String payloadDataTextContent = payloadData.getTextContent();
        Document document;
        try {
            document = builder.parse(new InputSource(new ByteArrayInputStream(payloadDataTextContent.getBytes("utf-8"))));
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        NodeList messageElement = document.getElementsByTagName("jpId");
        String jpId = messageElement.item(0).getTextContent();
        File payloadFile = new File(System.getProperty("user.home") + File.separator + "testToRemove" + File.separator + "payloadFromNoArkive.xml");
        try {
            FileUtils.writeByteArrayToFile(payloadFile, payloadDataTextContent.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        KnutepunktContext context = new KnutepunktContext();
        context.setJpId(jpId);
        try {
            if (message.getEnvelope() != null && message.getEnvelope().getSender() != null) {
                verifySender(message.getEnvelope().getSender(), context);
            } else {
                return createErrorResponse("no sender");
            }
            if (message.getEnvelope().getReceiver() != null) {
                verifyRecipient(message.getEnvelope().getReceiver(), context);
            } else {
                return createErrorResponse("no receiver");
            }
            eventLog.log(createOkStateEvent(message, ProcessState.SIGNATURE_VALIDATED));

        } catch (InvalidSender | InvalidReceiver e) {
            Event errorEvent = createErrorEvent(message, ProcessState.SIGNATURE_VALIDATION_ERROR, e);
            eventLog.log(errorEvent);
            return createErrorResponse();
        }

        SBD sbd = createSBD(message, context);
        try {
            sendSBD(sbd);
        } catch (IOException e) {
            eventLog.log(createErrorEvent(message, ProcessState.MESSAGE_SEND_FAIL, e));
            return createErrorResponse();
        }
        eventLog.log(createOkStateEvent(message, ProcessState.SBD_SENT));
        return new PutMessageResponseType();
    }


    private PutMessageResponseType createErrorResponse() {
        return createErrorResponse("ERROR_INVALID_OR_MISSING_SENDER");
    }


    //TODO Maye return SOAP Fault?
    private PutMessageResponseType createErrorResponse(String message) {
        PutMessageResponseType response = new PutMessageResponseType();
        AppReceiptType receipt = new AppReceiptType();
        receipt.setType(message);
        response.setResult(receipt);
        return response;
    }

    PrivateKey findPrivateKey() {
        PrivateKey key = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("knutepunkt_privatekey.pkcs8"),
                Charset.forName("UTF-8")))) {
            StringBuilder builder = new StringBuilder();
            boolean inKey = false;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!inKey && line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")) {
                    inKey = true;
                } else {
                    if (line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = false;
                    }
                    builder.append(line);
                }
            }

            byte[] encoded = DatatypeConverter.parseBase64Binary(builder.toString());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            key = kf.generatePrivate(keySpec);

        } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return key;
    }

    public void setAdresseregister(AdresseregisterService adresseregister) {
        this.adresseregister = adresseregister;
    }

    public void setEventLog(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    private Event createErrorEvent(PutMessageRequestType anyOject, ProcessState state, Exception e) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setExceptionMessage(event.getMessage());
        event.setProcessStates(state);
        event.setMessage(xs.toXML(anyOject));
        return event;
    }

    private Event createOkStateEvent(PutMessageRequestType anyOject, ProcessState state) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setMessage(xs.toXML(anyOject));
        event.setProcessStates(state);
        return event;
    }

}

