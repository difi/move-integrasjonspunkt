package no.difi.meldingsutveksling.noarkexchange.altinn;


import no.difi.meldingsutveksling.AltinnWsClient;
import no.difi.meldingsutveksling.AltinnWsConfiguration;
import no.difi.meldingsutveksling.DownloadRequest;
import no.difi.meldingsutveksling.FileReference;
import no.difi.meldingsutveksling.config.IntegrasjonspunktConfig;
import no.difi.meldingsutveksling.dokumentpakking.xml.Payload;
import no.difi.meldingsutveksling.domain.sbdh.Document;
import no.difi.meldingsutveksling.domain.sbdh.ObjectFactory;
import no.difi.meldingsutveksling.noarkexchange.IntegrajonspunktReceiveImpl;
import no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * MessagePolling periodically checks Altinn Formidlingstjeneste for new messages. If new messages are discovered they
 * are downloaded forwarded to the Archive system.
 */
@Component
public class MessagePolling {
    Logger logger = LoggerFactory.getLogger(MessagePolling.class);

    @Autowired
    IntegrasjonspunktConfig config;

    @Autowired
    IntegrajonspunktReceiveImpl integrajonspunktReceive;

    private static JAXBContext jaxbContextdomain;

    static {
        try {
            jaxbContext = JAXBContext.newInstance(StandardBusinessDocument.class, Payload.class);
            jaxbContextdomain = JAXBContext.newInstance(Document.class, Payload.class);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    private static JAXBContext jaxbContext;

    @Scheduled(fixedRate = 15000)
    public void checkForNewMessages() {
        logger.debug("Checking for new messages");

        AltinnWsConfiguration configuration = AltinnWsConfiguration.fromConfiguration(config.getConfiguration());
        AltinnWsClient client = new AltinnWsClient(configuration);

        List<FileReference> fileReferences = client.availableFiles(config.getOrganisationNumber());

        for(FileReference reference : fileReferences) {
            Document document = client.download(new DownloadRequest(reference.getValue(), config.getOrganisationNumber()));
            forwardToNoark(document);

        }
    }

    private void forwardToNoark(Document document) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            JAXBElement<Document> d = new ObjectFactory().createStandardBusinessDocument(document);

            jaxbContextdomain.createMarshaller().marshal(d, os);
            byte[] tmp = os.toByteArray();

            JAXBElement<no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument> toDocument
                    = (JAXBElement<no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument>)
                    jaxbContext.createUnmarshaller().unmarshal(new ByteArrayInputStream(tmp));

            integrajonspunktReceive.forwardToNoarkSystem(toDocument.getValue());
        } catch (JAXBException e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
        }
    }
}

