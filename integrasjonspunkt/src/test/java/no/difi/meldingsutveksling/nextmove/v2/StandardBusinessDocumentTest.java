package no.difi.meldingsutveksling.nextmove.v2;

import no.difi.meldingsutveksling.domain.sbdh.*;
import no.difi.meldingsutveksling.nextmove.NextMoveOutMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class StandardBusinessDocumentTest {
  private StandardBusinessDocument sbd;
  private ZonedDateTime expected;

    @Before
    public void setUp() {
        sbd = getStandardBusinessDocument();
        expected = ZonedDateTime.parse("2025-02-10T00:31:52Z");
    }

    @Test
    public void isExpectedResponseDateTimeExpired_ShouldNotBeExpired() {
        ZonedDateTime expectedResponseDateTime = sbd.getExpectedResponseDateTime();
        ZonedDateTime now = ZonedDateTime.now();
        assertTrue(expectedResponseDateTime.isAfter(now));
    }

    @Test
    public void getExpectedResponseDateTime_shouldReturnExpectedResponseDateTime() {
        ZonedDateTime expectedResponseDateTime = sbd.getExpectedResponseDateTime();
        assertEquals(expected, expectedResponseDateTime);
    }

    private StandardBusinessDocument getStandardBusinessDocument() {
        return new StandardBusinessDocument()
                .setStandardBusinessDocumentHeader(new StandardBusinessDocumentHeader()
                        .setBusinessScope(new BusinessScope()
                                .addScope(new Scope()
                                        .addScopeInformation(new CorrelationInformation()
                                                .setExpectedResponseDateTime(ZonedDateTime.parse("2025-02-10T00:31:52Z"))
                                        )
                                        .setIdentifier("urn:no:difi:meldingsutveksling:2.0")
                                        .setInstanceIdentifier("37efbd4c-413d-4e2c-bbc5-257ef4a65a45")
                                        .setType("ConversationId")
                                )
                        )
                        .setDocumentIdentification(new DocumentIdentification()
                                .setCreationDateAndTime(ZonedDateTime.parse("2025-01-11T15:29:58.753+02:00"))
                                .setInstanceIdentifier("ff88849c-e281-4809-8555-7cd54952b916")
                                .setStandard("urn:no:difi:meldingsutveksling:2.0")
                                .setType("DPO")
                                .setTypeVersion("2.0")
                        )
                        .setHeaderVersion("1.0")
                        .addReceiver(new Receiver()
                                .setIdentifier(new PartnerIdentification()
                                        .setAuthority("iso6523-actorid-upis")
                                        .setValue("9908:910075918")
                                )
                        )
                        .addSender(new Sender()
                                .setIdentifier(new PartnerIdentification()
                                        .setAuthority("iso6523-actorid-upis")
                                        .setValue("9908:910077473")
                                )
                        )
                )
                .setAny(new NextMoveOutMessage()
                        .setConversationId("text")
                );
    }
}
