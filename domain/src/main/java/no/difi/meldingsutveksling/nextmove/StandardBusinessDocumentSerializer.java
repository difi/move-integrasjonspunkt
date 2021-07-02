package no.difi.meldingsutveksling.nextmove;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;

import java.io.IOException;

public class StandardBusinessDocumentSerializer extends JsonSerializer<StandardBusinessDocument> {

    @Override
    public Class<StandardBusinessDocument> handledType() {
        return StandardBusinessDocument.class;
    }

    @Override
    public void serialize(StandardBusinessDocument value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeObjectField("standardBusinessDocumentHeader", value.getStandardBusinessDocumentHeader());
        gen.writeObjectField(value.getType().orElse("any"), value.getAny());
        gen.writeEndObject();
    }
}
