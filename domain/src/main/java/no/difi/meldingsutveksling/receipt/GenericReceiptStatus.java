package no.difi.meldingsutveksling.receipt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

@XmlType
@XmlEnum(String.class)
public enum GenericReceiptStatus implements ReceiptStatus {
    @XmlEnumValue("SENT") SENT("Sent"),
    @XmlEnumValue("DELIVERED")DELIVERED("Delivered"),
    @XmlEnumValue("READ") READ("Read"),
    @XmlEnumValue("FAIL") FAIL("Fail"),
    @XmlEnumValue("OTHER") OTHER("Other");

    private final String status;

    GenericReceiptStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

}
