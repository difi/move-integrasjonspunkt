package no.difi.meldingsutveksling.dpi;

import no.difi.meldingsutveksling.config.DigitalPostInnbyggerConfig;
import no.difi.sdp.client2.domain.digital_post.DigitalPost;

public abstract class DigitalPostBuilderHandler {
    private final DigitalPostInnbyggerConfig config;

    public DigitalPostBuilderHandler(DigitalPostInnbyggerConfig config) {
        this.config = config;
    }

    public abstract DigitalPost.Builder handle(MeldingsformidlerRequest request, DigitalPost.Builder builder);

    public DigitalPostInnbyggerConfig getConfig() {
        return config;
    }
}
