package no.difi.meldingsutveksling.nextmove;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlRootElement;

@Data
@Entity
@DiscriminatorValue("dpv")
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement(name = "dpv", namespace = "urn:no:difi:meldingsutveksling:2.0")
public class DpvMessage extends BusinessMessage {
    private String dpvField;
}