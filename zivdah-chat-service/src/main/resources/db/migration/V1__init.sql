CREATE TABLE support_agents (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE, display_name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, max_concurrent_chats INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY, customer_id BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL DEFAULT 'BOT' CHECK (type IN ('BOT','HUMAN')),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','WAITING','ACTIVE','CLOSED')),
    assigned_agent_id BIGINT NULL, order_id BIGINT NULL,
    topic VARCHAR(30) NULL,           -- ORDER_STATUS/REFUND/PRODUCT/DELIVERY/ACCOUNT/GENERAL, set by intent classifier, feeds analytics
    handed_off_at TIMESTAMP NULL,     -- BOT->HUMAN transition time; feeds "first response time" metric
    created_at TIMESTAMP NOT NULL DEFAULT now(), updated_at TIMESTAMP NOT NULL DEFAULT now(), closed_at TIMESTAMP NULL
);
CREATE INDEX idx_conversations_customer_id ON conversations(customer_id);
CREATE INDEX idx_conversations_status ON conversations(status);
CREATE INDEX idx_conversations_assigned_agent_id ON conversations(assigned_agent_id);

CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY, conversation_id BIGINT NOT NULL, sender_id BIGINT NULL,
    sender_type VARCHAR(10) NOT NULL CHECK (sender_type IN ('CUSTOMER','BOT','AGENT','SYSTEM')),
    message_type VARCHAR(10) NOT NULL DEFAULT 'TEXT' CHECK (message_type IN ('TEXT','IMAGE','FILE','SYSTEM')),
    message VARCHAR(4000) NOT NULL, attachment_url VARCHAR(500) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT','DELIVERED','READ')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation_id_id ON messages(conversation_id, id);

CREATE TABLE conversation_ratings (
    id BIGSERIAL PRIMARY KEY, conversation_id BIGINT NOT NULL UNIQUE, customer_id BIGINT NOT NULL,
    agent_id BIGINT NULL, rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    feedback VARCHAR(1000) NULL, created_at TIMESTAMP NOT NULL DEFAULT now()
);
