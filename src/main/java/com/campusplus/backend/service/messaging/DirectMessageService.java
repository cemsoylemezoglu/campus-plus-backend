package com.campusplus.backend.service.messaging;

import com.campusplus.backend.dto.messaging.*;
import com.campusplus.backend.dto.messaging.MessagingResponses.*;
import com.campusplus.backend.entity.*;
import com.campusplus.backend.exception.*;
import com.campusplus.backend.repository.*;
import com.campusplus.backend.security.CurrentActor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class DirectMessageService {
    private final DirectMessageWorkspace workspace;
    private final DirectConversationRepository conversations;
    private final DirectMessageRepository messages;
    private final DirectConversationStateRepository states;
    private final UserBlockRepository blocks;
    private final CurrentActor current;
    private final Clock clock;
    public DirectMessageService(DirectMessageWorkspace workspace, DirectConversationRepository conversations,
            DirectMessageRepository messages, DirectConversationStateRepository states, UserBlockRepository blocks,
            CurrentActor current, Clock clock) {
        this.workspace = workspace; this.conversations = conversations; this.messages = messages;
        this.states = states; this.blocks = blocks; this.current = current; this.clock = clock;
    }
    public Message start(StartDirectConversationRequest request) {
        var pair = workspace.lockPair(request.recipientUserId());
        var existing = conversations.findPair(pair.members().lowerUserId(), pair.members().higherUserId());
        DirectConversation conversation;
        if (existing.isPresent()) conversation = workspace.lockConversation(existing.get().getId());
        else conversation = conversations.saveAndFlush(new DirectConversation(pair.actor(), pair.recipient(), now()));
        workspace.requireWritable(pair);
        if (existing.isEmpty()) {
            states.save(new DirectConversationState(conversation, pair.actor().getId(), now()));
            states.save(new DirectConversationState(conversation, pair.recipient().getId(), now()));
        }
        return sendLocked(conversation, pair.actor(), request.message());
    }
    public Message send(long conversationId, SendDirectMessageRequest request) {
        var preview = workspace.readable(conversationId);
        var members = preview.members(); long actor = current.userId();
        long recipientId = members.lowerUserId() == actor ? members.higherUserId() : members.lowerUserId();
        var pair = workspace.lockPair(recipientId);
        var conversation = workspace.lockConversation(conversationId);
        workspace.requireWritable(pair);
        return sendLocked(conversation, pair.actor(), request);
    }
    private Message sendLocked(DirectConversation conversation, User actor, SendDirectMessageRequest request) {
        var previous = messages.findByConversationIdAndSenderIdAndClientMessageId(conversation.getId(), actor.getId(), request.clientMessageId());
        if (previous.isPresent()) {
            if (!previous.get().getBody().equals(request.body()))
                throw new ResourceConflictException("İstek anahtarı farklı bir mesaj için kullanılmış.");
            return Message.from(previous.get());
        }
        var message = new DirectMessage(conversation, actor, request.clientMessageId(), request.body(), now());
        return Message.from(messages.saveAndFlush(message));
    }
    public ReadState markRead(long id, ReadDirectConversationRequest request) {
        // No user lock after the conversation lock. Reading never changes contact eligibility.
        var conversation = workspace.lockConversation(id);
        long number = request.throughMessageNumber();
        if (number > conversation.getLastMessageNumber() || !messages.existsByConversationIdAndMessageNumber(id, number))
            throw new InvalidRequestException("Okundu konumu bu konuşmada bulunamadı.");
        var state = states.findByConversationIdAndUserId(id, current.userId()).orElseThrow(DirectMessageWorkspace::missing);
        state.markRead(number, now()); states.save(state);
        return new ReadState(id, state.getLastReadMessageNumber());
    }
    public void block(long userId, boolean active) {
        var pair = workspace.lockPair(userId);
        var existing = blocks.findByBlockerIdAndBlockedId(pair.actor().getId(), pair.recipient().getId());
        if (existing.isPresent()) {
            var block = existing.get(); block.setActive(active, now()); blocks.save(block);
        } else if (active) blocks.save(new UserBlock(pair.actor(), pair.recipient(), now()));
    }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
}
