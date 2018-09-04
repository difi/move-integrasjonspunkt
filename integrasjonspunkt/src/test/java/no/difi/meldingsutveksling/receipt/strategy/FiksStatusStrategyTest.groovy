package no.difi.meldingsutveksling.receipt.strategy

import no.difi.meldingsutveksling.ServiceIdentifier
import no.difi.meldingsutveksling.ks.receipt.DpfReceiptStatus
import no.difi.meldingsutveksling.ks.svarut.SvarUtService
import no.difi.meldingsutveksling.nextmove.ConversationDirection
import no.difi.meldingsutveksling.receipt.Conversation
import no.difi.meldingsutveksling.receipt.ConversationService
import no.difi.meldingsutveksling.receipt.GenericReceiptStatus
import no.difi.meldingsutveksling.receipt.MessageStatus
import spock.lang.*
import sun.plugin2.message.Message

class FiksStatusStrategyTest extends Specification {

    @Unroll
    "given message receipt with status #status, conversation should be set non pollable"() {
        given:
        SvarUtService svarUtService = Mock(SvarUtService)
        ConversationService conversationService = Mock(ConversationService)
        Conversation conversation = Mock(Conversation)

        when:
        def messageReceipt = MessageStatus.of(status)
        FiksStatusStrategy strategy = new FiksStatusStrategy(svarUtService, conversationService)
        svarUtService.getMessageReceipt(_) >> messageReceipt
        conversationService.registerStatus(_ as Conversation, _ as MessageStatus) >> conversation
        conversation.getReceiverIdentifier() >> "123456785"
        conversation.getServiceIdentifier() >> ServiceIdentifier.DPF
        conversation.getDirection() >> ConversationDirection.OUTGOING
        conversation.getConversationId() >> UUID.randomUUID().toString()

        strategy.checkStatus(conversation)

        then:
        1 * conversationService.markFinished(_)

        where:
        status << [DpfReceiptStatus.LEST, DpfReceiptStatus.IKKE_LEVERT, DpfReceiptStatus.AVVIST, DpfReceiptStatus.MANULT_HANDTERT, GenericReceiptStatus.FEIL]
    }


}
