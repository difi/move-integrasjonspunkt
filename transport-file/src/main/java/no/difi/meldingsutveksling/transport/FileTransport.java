package no.difi.meldingsutveksling.transport;

import no.difi.meldingsutveksling.dokumentpakking.xml.Payload;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.domain.sbdh.EduDocument;
import no.difi.meldingsutveksling.domain.sbdh.ObjectFactory;
import no.difi.meldingsutveksling.kvittering.xsd.Kvittering;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Oxalis implementation of the trasnport interface. Uses the oxalis outbound module to transmit the SBD
 *
 * @author Glenn Bech
 */
public class FileTransport implements Transport {

    @Override
    public void send(ApplicationContext context, EduDocument eduDocument) {
        String fileName = createFilename();
        ModelMapper mapper = new ModelMapper();

        no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument toWrite =
                mapper.map(eduDocument, no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument.class);

        File f = new File(fileName);
        try {
            JAXBContext jaxbContext = JAXBContextFactory.createContext(new Class[]{EduDocument.class, Payload.class, Kvittering.class}, null);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(new ObjectFactory().createStandardBusinessDocument(eduDocument), new FileOutputStream(f));
            JAXBElement<no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument> element =
                    new JAXBElement<>(new QName("ns"),
                            no.difi.meldingsutveksling.noarkexchange.schema.receive.StandardBusinessDocument.class, toWrite);
        } catch (JAXBException e) {
            throw new MeldingsUtvekslingRuntimeException("file write error ", e);
        } catch (FileNotFoundException e) {
            throw new MeldingsUtvekslingRuntimeException();
        }
    }

    private String createFilename() {
        return System.currentTimeMillis() + ".xml";
    }

}
