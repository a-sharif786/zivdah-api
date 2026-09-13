package com.zivdah.chat.chatbot;

import reactor.core.publisher.Mono;

// Pluggable chatbot backend. RuleBasedChatbotProvider is the only registered @Service today
// (a demo/free-tier keyword-matching implementation — see its own Javadoc). A real LLM later is
// a second bean marked @Primary (or gated by a chatbot.provider=llm property) implementing this
// same interface — ChatbotServiceImpl depends only on the interface, so no other code changes.
public interface ChatbotProvider {
    Mono<BotReply> reply(String userMessage, ChatContext context);
}
