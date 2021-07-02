package no.difi.meldingsutveksling.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import no.difi.meldingsutveksling.jpa.ObjectMapperHolder;
import no.difi.meldingsutveksling.nextmove.StandardBusinessDocumentDeserializer;
import no.difi.meldingsutveksling.nextmove.StandardBusinessDocumentSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

import static no.difi.meldingsutveksling.DateTimeUtil.DEFAULT_ZONE_ID;

@Configuration
public class JacksonConfig {

    @Bean
    @SuppressWarnings("deprecation") // JsonReadFeature not yet supported by builder
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer(Clock clock) {

        return builder ->
                builder.modulesToInstall(new JavaTimeModule())
                        .deserializerByType(OffsetDateTime.class, new IsoDateTimeDeserializer(clock))
                        .serializationInclusion(JsonInclude.Include.NON_NULL)
                        .serializers(new StandardBusinessDocumentSerializer())
                        .deserializers(new StandardBusinessDocumentDeserializer())
                        .featuresToEnable(
                                SerializationFeature.INDENT_OUTPUT,
                                JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS,
                                MapperFeature.DEFAULT_VIEW_INCLUSION)
                        .featuresToDisable(
                                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                                SerializationFeature.CLOSE_CLOSEABLE,
                                DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @SuppressWarnings("squid:MaximumInheritanceDepth")
    private static final class IsoDateTimeDeserializer extends InstantDeserializer<OffsetDateTime> {

        IsoDateTimeDeserializer(Clock clock) {
            super(
                    OffsetDateTime.class,
                    DateTimeFormatter.ISO_DATE_TIME,
                    temporal -> getOffsetDateTime(clock, temporal),
                    a -> OffsetDateTime.ofInstant(Instant.ofEpochMilli(a.value), a.zoneId),
                    a -> OffsetDateTime.ofInstant(Instant.ofEpochSecond(a.integer, a.fraction), a.zoneId),
                    (d, z) -> d.withOffsetSameInstant(z.getRules().getOffset(d.toLocalDateTime())),
                    true // yes, replace +0000 with Z
            );
        }

        private static OffsetDateTime getOffsetDateTime(Clock clock, TemporalAccessor temporal) {
            ZoneId obj = temporal.query(TemporalQueries.zone());

            if (obj != null) {
                return OffsetDateTime.from(temporal);
            }

            return LocalDateTime.from(temporal)
                    .atOffset(DEFAULT_ZONE_ID.getRules().getOffset(LocalDateTime.now(clock)));
        }
    }

    @Bean
    public ObjectMapperHolder objectMapperHolder(Jackson2ObjectMapperBuilder objectMapperBuilder) {
        return new ObjectMapperHolder(objectMapperBuilder.build());
    }
}
