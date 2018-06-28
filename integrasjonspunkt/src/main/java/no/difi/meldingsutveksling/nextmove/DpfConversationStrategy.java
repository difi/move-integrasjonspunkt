package no.difi.meldingsutveksling.nextmove;

import no.difi.meldingsutveksling.ks.svarut.SvarUtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DpfConversationStrategy implements ConversationStrategy {

    private SvarUtService svarUtService;

    @Autowired
    DpfConversationStrategy(SvarUtService svarUtService) {
        this.svarUtService = svarUtService;
    }

    @Override
    public void send(ConversationResource cr) throws NextMoveException {
        svarUtService.send(cr);
    }
}
