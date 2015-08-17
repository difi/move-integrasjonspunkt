package no.difi.meldingsutveksling;

import no.altinn.schema.services.serviceengine.broker._2015._06.BrokerServiceManifest;
import no.altinn.schema.services.serviceengine.broker._2015._06.BrokerServiceRecipientList;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.shipping.ExternalServiceBuilder;
import no.difi.meldingsutveksling.shipping.ManifestBuilder;
import no.difi.meldingsutveksling.shipping.RecipientBuilder;
import no.difi.meldingsutveksling.shipping.Request;

import javax.xml.bind.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.math.BigInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Represents an Altinn package to be used with the formidlingstjeneste SFTP channel.
 *
 * Has factory methods of writing/reading from to zip files via input/output streams.
 */
public class AltinnPackage {
    private static JAXBContext ctx;
    private final BrokerServiceManifest manifest;
    private final BrokerServiceRecipientList recipient;
    private final StandardBusinessDocument document;

    static {
        try {
            ctx = JAXBContext.newInstance(BrokerServiceManifest.class, BrokerServiceRecipientList.class, StandardBusinessDocument.class);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
    }

    private AltinnPackage(BrokerServiceManifest manifest, BrokerServiceRecipientList recipient, StandardBusinessDocument document) {
        this.manifest = manifest;
        this.recipient = recipient;
        this.document = document;
    }

    public static AltinnPackage from(Request document) {
        ManifestBuilder manifest = new ManifestBuilder();
        manifest.withSender(document.getSender());
        manifest.withSenderReference(document.getSenderReference());
        manifest.withExternalService(
                new ExternalServiceBuilder()
                .withExternalServiceCode("v3888")
                .withExternalServiceEditionCode(new BigInteger("070515"))
                .build());

        RecipientBuilder recipient = new RecipientBuilder(document.getReceiver());
        return new AltinnPackage(manifest.build(), recipient.build(), document.getPayload());
    }

    public byte[] getManifestContent() {
        return marshallObject(manifest);
    }

    public byte[] getRecipientsContent() {
        return marshallObject(recipient);
    }

    public byte[] getPayload() {
        no.difi.meldingsutveksling.domain.sbdh.ObjectFactory objectFactory = new no.difi.meldingsutveksling.domain.sbdh.ObjectFactory();
        return marshallObject(objectFactory.createStandardBusinessDocument(document));
    }

    /**
     * Writes the Altinn package as a Zip file
     * @param outputStream where the Zip file is written
     * @throws IOException
     */
    public void write(OutputStream outputStream) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

        zipContent(zipOutputStream, "manifest.xml", getManifestContent());

        zipContent(zipOutputStream, "recipients.xml", getRecipientsContent());

        zipContent(zipOutputStream, "content.xml", getPayload());

        zipOutputStream.finish();
        zipOutputStream.flush();
        zipOutputStream.close();
    }

    public static AltinnPackage from(InputStream inputStream) throws IOException, JAXBException {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        InputStream inputStreamProxy = new FilterInputStream(zipInputStream) {
            @Override
            public void close() throws IOException {
                // do nothing to avoid unmarshaller to close it before the Zip file is fully processed
            }
        };

        Unmarshaller unmarshaller = ctx.createUnmarshaller();

        ZipEntry zipEntry;
        BrokerServiceManifest manifest = null;
        BrokerServiceRecipientList recipientList = null;
        StandardBusinessDocument document = null;
        while((zipEntry = zipInputStream.getNextEntry()) != null) {
            if(zipEntry.getName().equals("manifest.xml")) {
                 manifest = (BrokerServiceManifest) unmarshaller.unmarshal(inputStreamProxy);
            } else if(zipEntry.getName().equals("recipients.xml")) {
                 recipientList = (BrokerServiceRecipientList) unmarshaller.unmarshal(inputStreamProxy);
            } else if(zipEntry.getName().equals("content.xml")) {
                Source source = new StreamSource(inputStreamProxy);
                document = unmarshaller.unmarshal(source, StandardBusinessDocument.class).getValue();
            }
        }

        return new AltinnPackage(manifest, recipientList, document);
    }

    private void zipContent(ZipOutputStream zipOutputStream, String fileName, byte[] fileContent) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        zipOutputStream.write(fileContent);
        zipOutputStream.closeEntry();
    }

    private byte[] marshallObject(Object object) {
        try {

            Marshaller marshaller = ctx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);


            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            marshaller.marshal(object, outputStream);
            return outputStream.toByteArray();
        } catch (JAXBException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public StandardBusinessDocument getDocument() {
        return document;
    }
}
