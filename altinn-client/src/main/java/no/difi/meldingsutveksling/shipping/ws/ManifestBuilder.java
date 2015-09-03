package no.difi.meldingsutveksling.shipping.ws;

import no.difi.meldingsutveksling.altinn.mock.brokerbasic.Manifest;

public class ManifestBuilder {
    //private ExternalService externalService;
    private String sender;
    private String senderReference;
    private String filename;

    public ManifestBuilder withSender(String sender) {
        this.sender = sender;
        return this;
    }

    public ManifestBuilder withSenderReference(String senderReference) {
        this.senderReference = senderReference;
        return this;
    }

    public ManifestBuilder withFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public Manifest build() {
        Manifest manifest = new Manifest();
        manifest.setReportee(sender);
        manifest.setSendersReference(senderReference);
        manifest.setFileList(new FileListBuilder().withFilename(filename).build());
        return manifest;
    }

}
