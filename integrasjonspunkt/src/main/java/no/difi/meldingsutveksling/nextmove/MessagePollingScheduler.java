package no.difi.meldingsutveksling.nextmove;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.difi.meldingsutveksling.api.DpePolling;
import no.difi.meldingsutveksling.api.DpfPolling;
import no.difi.meldingsutveksling.api.DpoPolling;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * MessagePolling periodically checks Altinn Formidlingstjeneste for new messages. If new messages are discovered they are
 * downloaded forwarded to the Archive system.
 */
@Slf4j
@RequiredArgsConstructor
public class MessagePollingScheduler {

    private final ObjectProvider<DpePolling> dpePolling;
    private final ObjectProvider<DpfPolling> dpfPolling;
    private final ObjectProvider<DpoPolling> dpoPolling;

    @Scheduled(fixedDelayString = "${difi.move.nextmove.serviceBus.pollingrate}")
    public void checkForNewEinnsynMessages() {
        dpePolling.orderedStream().findFirst().ifPresent(DpePolling::poll);
    }

    @Scheduled(fixedDelayString = "${difi.move.fiks.pollingrate}")
    public void checkForFiksMessages() {
        dpfPolling.orderedStream().findFirst().ifPresent(DpfPolling::poll);
    }

    @Scheduled(fixedDelay = 15000)
    public void checkForNewAltinnMessages() {
        dpoPolling.orderedStream().findFirst().ifPresent(DpoPolling::poll);
    }
}
