-- fk_conversation_agent existed only in the live DB (not in any checked-in migration) and pointed
-- at support_agents(id) — the roster's own surrogate PK. Every write/read of assigned_agent_id in
-- the app (acceptConversation, resolveDisplayName, getMine, requireSenderAllowed) treats it as a
-- users.id (the JWT subject), matching support_agents.user_id instead. Re-point the FK at the
-- column the app actually uses. Idempotent: safe to (re)run whether or not the stray constraint
-- is present, and whichever column it was pointing at.
ALTER TABLE conversations DROP CONSTRAINT IF EXISTS fk_conversation_agent;
ALTER TABLE conversations
    ADD CONSTRAINT fk_conversation_agent FOREIGN KEY (assigned_agent_id) REFERENCES support_agents(user_id);
