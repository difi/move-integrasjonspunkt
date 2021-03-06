package no.difi.meldingsutveksling.shipping.ws;

import lombok.Value;

/**
 * Class to contain error String messages from Altinn soap faults
 */
@Value
public class AltinnReason {

    Integer id;
    String message;
    String userId;
    String localized;

    @Override
    public String toString() {
        return String.format("Reason: %s. LocalizedErrorMessage: %s. ErrorId: %d. UserId: %s", message, localized, id, userId);
    }

}
