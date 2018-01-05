package no.difi.meldingsutveksling.dokumentpakking.xml;

import no.difi.meldingsutveksling.domain.Organisasjonsnummer;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MarshalManifestTest {
	@Test
	public void testMarshalling() throws JAXBException {

		Manifest original = new Manifest(new Mottaker(new Organisasjon(new Organisasjonsnummer("12345678"))), new Avsender(new Organisasjon(
				new Organisasjonsnummer("12345678"))), new HovedDokument("lol.pdf", "application/pdf", "Hoveddokument", "no"));

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		MarshalManifest.marshal(original, os);

		InputStream is = new ByteArrayInputStream(os.toByteArray());

		JAXBContext jaxbContext = JAXBContextFactory.createContext(new Class[]{Manifest.class}, null);
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		Manifest kopi = (Manifest) jaxbUnmarshaller.unmarshal(is);
		assertThat(kopi.getHoveddokument().getTittel().getTittel(), is(original.getHoveddokument().getTittel().getTittel()));
		assertThat(kopi.getAvsender().getOrganisasjon().getOrgNummer(), is(original.getAvsender().getOrganisasjon().getOrgNummer()));

	}

}
