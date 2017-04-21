package no.difi.meldingsutveksling.receipt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import lombok.Data;
import no.difi.meldingsutveksling.ServiceIdentifier;
import no.difi.meldingsutveksling.core.EDUCore;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Data
public class Conversation {

    @Id
    @GeneratedValue
    private Integer convId;
    private String conversationId;
    private String receiverIdentifier;
    private String messageReference;
    private String messageTitle;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime lastUpdate;
    @JsonIgnore
    private boolean pollable;
    private ServiceIdentifier serviceIdentifier;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "conv_id")
    private List<MessageReceipt> messageReceipts;

    Conversation() {}

    private Conversation(String conversationId,
                         String messageReference,
                         String receiverIdentifier,
                         String messageTitle,
                         ServiceIdentifier serviceIdentifier,
                         List<MessageReceipt> receipts) {
        this.conversationId = conversationId;
        this.messageReference = messageReference;
        this.receiverIdentifier = receiverIdentifier;
        this.messageTitle = messageTitle;
        this.messageReceipts = receipts;
        this.serviceIdentifier = serviceIdentifier;
        this.lastUpdate = LocalDateTime.now();
        switch (serviceIdentifier) {
            case DPV:
            case DPF:
                this.pollable = true;
                break;
            case DPI:
            case DPO:
            default:
                this.pollable = false;
                break;
        }
    }

    public static Conversation of(String conversationId,
                                  String messageReference,
                                  String receiverIdentifier,
                                  String messageTitle,
                                  ServiceIdentifier serviceIdentifier,
                                  MessageReceipt... receipts) {
        if (receipts == null || receipts.length == 0) {
            return new Conversation(conversationId, messageReference, receiverIdentifier, messageTitle,
                    serviceIdentifier, Lists.newArrayList());
        }
        List<MessageReceipt> receiptList = Stream.of(receipts)
                .peek(r -> r.setConversationId(conversationId))
                .collect(Collectors.toList());
        return new Conversation(conversationId, messageReference, receiverIdentifier, messageTitle,
                serviceIdentifier, receiptList);
    }

    public static Conversation of(EDUCore eduCore, MessageReceipt... receipts) {
        String msgTitle = "";
        if (eduCore.getMessageType() == EDUCore.MessageType.EDU) {
            msgTitle = eduCore.getPayloadAsMeldingType().getJournpost().getJpInnhold();
        }
        List<MessageReceipt> receiptList = Stream.of(receipts)
                .peek(r -> r.setConversationId(eduCore.getId()))
                .collect(Collectors.toList());
        return new Conversation(eduCore.getId(), eduCore.getMessageReference(), eduCore.getReceiver().getIdentifier(),
                msgTitle, eduCore.getServiceIdentifier() , receiptList);
    }

    public void addMessageReceipt(MessageReceipt receipt) {
        receipt.setConversationId(getConversationId());
        this.messageReceipts.add(receipt);
        this.lastUpdate = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("convId", convId)
                .add("conversationId", conversationId)
                .add("messageReference", messageReference)
                .add("receiverIdentifier", receiverIdentifier)
                .add("messageReference", messageReference)
                .add("messageTitle", messageTitle)
                .add("pollable", pollable)
                .add("serviceIdentifier", serviceIdentifier)
                .add("messageReceipts", messageReceipts)
                .toString();
    }
}
