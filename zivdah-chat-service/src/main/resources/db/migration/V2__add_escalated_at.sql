-- Tracks whether a WAITING conversation has already been escalated to admins once via
-- WaitingConversationEscalationScheduler, so a conversation nobody claims doesn't get a fresh
-- "New support request" notification every ~2 minutes forever — escalate once, not repeatedly.
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP NULL;
