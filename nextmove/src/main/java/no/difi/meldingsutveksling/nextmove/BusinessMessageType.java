package no.difi.meldingsutveksling.nextmove;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public enum BusinessMessageType {

    STATUS("status", StatusMessage.class),

    ARKIVMELDING("arkivmelding", ArkivmeldingMessage.class),
    ARKIVMELDING_KVITTERING("arkivmelding_kvittering", ArkivmeldingKvitteringMessage.class),

    AVTALT("avtalt", AvtaltMessage.class),

    FIKSIO("fiksio", FiksIoMessage.class),
    DIGITAL("digital", DpiDigitalMessage.class),
    DIGITAL_DPV("digital_dpv", DigitalDpvMessage.class),
    PRINT("print", DpiPrintMessage.class),

    INNSYNSKRAV("innsynskrav", InnsynskravMessage.class),
    PUBLISERING("publisering", PubliseringMessage.class),
    EINNSYN_KVITTERING("einnsyn_kvittering", EinnsynKvitteringMessage.class);

    private final String type;
    private final Class<? extends BusinessMessage<?>> clazz;

    BusinessMessageType(String type, Class<? extends BusinessMessage<?>> clazz) {
        this.type = type;
        this.clazz = clazz;
    }

    public static BusinessMessageType fromType(String type) {
        return Arrays.stream(BusinessMessageType.values()).filter(p -> p.getType().equalsIgnoreCase(type))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unknown BusinessMessageType = %s. Expecting one of %s",
                        type,
                        Arrays.stream(values()).map(BusinessMessageType::getType).collect(Collectors.joining(",")))));
    }
}
