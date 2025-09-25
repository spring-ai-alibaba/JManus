/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.manus.workspace.conversation.entity.po;

import jakarta.persistence.*;

import java.util.Date;

/**
 * @author dahua
 * @time 2025/8/5
 * @desc conversation message entity
 */
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private Date createTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", referencedColumnName = "conversationId", insertable = false, updatable = false)
    private MemoryEntity memoryEntity;

    public enum MessageType {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL_CALL,
        TOOL_RESPONSE
    }

    public ConversationMessage() {
        this.createTime = new Date();
    }

    public ConversationMessage(String conversationId, MessageType messageType, String content) {
        this.conversationId = conversationId;
        this.messageType = messageType;
        this.content = content;
        this.createTime = new Date();
    }

    public ConversationMessage(String conversationId, MessageType messageType, String content, String metadata) {
        this.conversationId = conversationId;
        this.messageType = messageType;
        this.content = content;
        this.metadata = metadata;
        this.createTime = new Date();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public MemoryEntity getMemoryEntity() {
        return memoryEntity;
    }

    public void setMemoryEntity(MemoryEntity memoryEntity) {
        this.memoryEntity = memoryEntity;
    }

    @Override
    public String toString() {
        return "ConversationMessage{" +
                "id=" + id +
                ", conversationId='" + conversationId + '\'' +
                ", messageType=" + messageType +
                ", content='" + content + '\'' +
                ", metadata='" + metadata + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
