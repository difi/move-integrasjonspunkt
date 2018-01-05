package no.difi.meldingsutveksling.receipt;

import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.dpi.MeldingsformidlerClient;
import no.difi.meldingsutveksling.KeystoreProvider;

public class DpiReceiptService {
    private IntegrasjonspunktProperties properties;
    private KeystoreProvider keystoreProvider;

    public DpiReceiptService(IntegrasjonspunktProperties properties, KeystoreProvider keystoreProvider) {
        this.properties = properties;
        this.keystoreProvider = keystoreProvider;
    }

    public ExternalReceipt checkForReceipts() {
        MeldingsformidlerClient client = new MeldingsformidlerClient(properties.getDpi(), keystoreProvider.getKeyStore());
        return client.sjekkEtterKvittering(properties.getOrg().getNumber());
    }
}
