package no.difi.meldingsutveksling.dpi;

import no.difi.meldingsutveksling.serviceregistry.externalmodel.PostAddress;

import java.util.List;

public class Request implements MeldingsformidlerRequest {
    private boolean notifiable;

    Request() {}

    @Override
    public Document getDocument() {
        return null;
    }

    @Override
    public List<Document> getAttachments() {
        return null;
    }

    @Override
    public String getMottakerPid() {
        return null;
    }

    @Override
    public String getSubject() {
        return null;
    }

    @Override
    public String getSenderOrgnumber() {
        return null;
    }

    @Override
    public String getConversationId() {
        return null;
    }

    @Override
    public String getPostkasseAdresse() {
        return null;
    }

    @Override
    public byte[] getCertificate() {
        return new byte[0];
    }

    @Override
    public String getOrgnrPostkasse() {
        return null;
    }

    @Override
    public String getEmailAddress() {
        return null;
    }

    @Override
    public String getSmsVarslingstekst() {
        return null;
    }

    @Override
    public String getEmailVarslingstekst() {
        return null;
    }

    @Override
    public String getMobileNumber() {
        return null;
    }

    @Override
    public boolean isNotifiable() {
        return notifiable;
    }

    @Override
    public boolean isPrintProvider() {
        return false;
    }

    @Override
    public PostAddress getPostAddress() {
        return null;
    }

    @Override
    public PostAddress getReturnAddress() {
        return null;
    }

    Request withNotifiable(boolean notifiable) {
        this.notifiable = notifiable;
        return this;
    }
}
