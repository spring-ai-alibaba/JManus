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
package com.alibaba.cloud.ai.manus.llm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.manus.config.ManusProperties;

/**
 * Service to automatically limit conversation memory size based on character count. Uses
 * LLM to summarize older dialog rounds while maintaining recent 5000 characters.
 *
 * @author jmanus
 */
@Service
public class ConversationMemoryLimitService {

	private static final Logger log = LoggerFactory.getLogger(ConversationMemoryLimitService.class);

	private static final int RECENT_CHARS_TO_KEEP = 5000;

	private static final int SUMMARY_MIN_CHARS = 3000;

	private static final int SUMMARY_MAX_CHARS = 4000;

	@Autowired
	private ManusProperties manusProperties;

	@Autowired
	private LlmService llmService;

	/**
	 * Check and limit conversation memory size for a given conversation ID. Maintains
	 * recent 5000 chars (at least one complete dialog round) and summarizes older rounds
	 * into a 3000-4000 char UserMessage.
	 * @param chatMemory The chat memory instance
	 * @param conversationId The conversation ID to check and limit
	 */
	public void checkAndLimitMemory(ChatMemory chatMemory, String conversationId) {
		if (chatMemory == null || conversationId == null || conversationId.trim().isEmpty()) {
			return;
		}

		try {
			List<Message> messages = chatMemory.get(conversationId);
			if (messages == null || messages.isEmpty()) {
				return;
			}

			int totalChars = calculateTotalCharacters(messages);
			int maxChars = getMaxCharacterCount();
			if (totalChars <= maxChars) {
				log.debug("Conversation memory size ({}) is within limit ({}) for conversationId: {}", totalChars,
						maxChars, conversationId);
				return;
			}

			log.info(
					"Conversation memory size ({}) exceeds limit ({}) for conversationId: {}. Summarizing older messages...",
					totalChars, maxChars, conversationId);

			// Summarize and trim messages
			summarizeAndTrimMessages(chatMemory, conversationId, messages);

		}
		catch (Exception e) {
			log.warn("Failed to check and limit conversation memory for conversationId: {}", conversationId, e);
		}
	}

	/**
	 * Calculate total character count of all messages.
	 * @param messages List of messages
	 * @return Total character count
	 */
	private int calculateTotalCharacters(List<Message> messages) {
		int totalChars = 0;
		for (Message message : messages) {
			String content = extractMessageContent(message);
			if (content != null) {
				totalChars += content.length();
			}
		}
		return totalChars;
	}

	/**
	 * Extract text content from a message.
	 * @param message The message
	 * @return Text content, or empty string if content cannot be extracted
	 */
	private String extractMessageContent(Message message) {
		if (message == null) {
			return "";
		}

		try {
			String text = message.getText();
			return text != null ? text : "";
		}
		catch (Exception e) {
			log.debug("Failed to extract content from message: {}", e.getMessage());
			return "";
		}
	}

	/**
	 * Summarize and trim messages: keep recent 5000 chars (at least one complete round),
	 * summarize older rounds into a 3000-4000 char UserMessage.
	 * @param chatMemory The chat memory instance
	 * @param conversationId The conversation ID
	 * @param messages Current list of messages
	 */
	private void summarizeAndTrimMessages(ChatMemory chatMemory, String conversationId, List<Message> messages) {
		// Group messages into dialog rounds (UserMessage + AssistantMessage pairs)
		List<DialogRound> dialogRounds = groupMessagesIntoRounds(messages);

		if (dialogRounds.isEmpty()) {
			log.warn("No dialog rounds found for conversationId: {}", conversationId);
			return;
		}

		// Find which rounds to keep and which to summarize
		// Strategy: Keep recent rounds up to 5000 chars, ensuring at least one complete
		// round
		List<DialogRound> roundsToKeep = new ArrayList<>();
		List<DialogRound> roundsToSummarize = new ArrayList<>();

		int accumulatedChars = 0;
		boolean hasKeptAtLeastOneRound = false;

		// Start from the newest round and work backwards
		for (int i = dialogRounds.size() - 1; i >= 0; i--) {
			DialogRound round = dialogRounds.get(i);
			int roundChars = round.getTotalChars();

			// If this is the newest round, always keep it (even if it exceeds 5000)
			if (i == dialogRounds.size() - 1) {
				// If newest round exceeds 5000 chars, summarize it but keep the summary
				if (roundChars > RECENT_CHARS_TO_KEEP) {
					UserMessage summarizedRound = summarizeRounds(List.of(round));
					DialogRound summarizedRoundObj = new DialogRound();
					summarizedRoundObj.addMessage(summarizedRound);
					roundsToKeep.add(0, summarizedRoundObj); // Add at beginning to
																// maintain order
					accumulatedChars += summarizedRound.getText().length();
				}
				else {
					roundsToKeep.add(0, round);
					accumulatedChars += roundChars;
				}
				hasKeptAtLeastOneRound = true;
			}
			else {
				// For other rounds, check if we can add them within 5000 char limit
				if (accumulatedChars + roundChars <= RECENT_CHARS_TO_KEEP) {
					roundsToKeep.add(0, round); // Add at beginning to maintain
												// chronological order
					accumulatedChars += roundChars;
					hasKeptAtLeastOneRound = true;
				}
				else {
					// Can't add this round, all remaining are older and should be
					// summarized
					for (int j = i; j >= 0; j--) {
						roundsToSummarize.add(0, dialogRounds.get(j));
					}
					break;
				}
			}
		}

		// Ensure we kept at least one round
		if (!hasKeptAtLeastOneRound && !dialogRounds.isEmpty()) {
			// Fallback: keep the newest round even if it exceeds limit
			DialogRound newestRound = dialogRounds.get(dialogRounds.size() - 1);
			if (newestRound.getTotalChars() > RECENT_CHARS_TO_KEEP) {
				UserMessage summarizedRound = summarizeRounds(List.of(newestRound));
				DialogRound summarizedRoundObj = new DialogRound();
				summarizedRoundObj.addMessage(summarizedRound);
				roundsToKeep.add(summarizedRoundObj);
			}
			else {
				roundsToKeep.add(newestRound);
			}
			// Add all others to summarize
			for (int i = 0; i < dialogRounds.size() - 1; i++) {
				roundsToSummarize.add(dialogRounds.get(i));
			}
		}

		// Summarize older rounds
		UserMessage summaryMessage = null;
		if (!roundsToSummarize.isEmpty()) {
			summaryMessage = summarizeRounds(roundsToSummarize);
		}

		// Rebuild memory: summary first, then recent rounds
		chatMemory.clear(conversationId);

		if (summaryMessage != null) {
			chatMemory.add(conversationId, summaryMessage);
			log.info("Added summarized message ({} chars) for conversationId: {}", summaryMessage.getText().length(),
					conversationId);
		}

		// Add recent rounds
		for (DialogRound round : roundsToKeep) {
			for (Message message : round.getMessages()) {
				chatMemory.add(conversationId, message);
			}
		}

		int keptChars = calculateTotalCharacters(
				roundsToKeep.stream().flatMap(round -> round.getMessages().stream()).toList());
		log.info(
				"Summarized conversation memory for conversationId: {}. Kept {} recent rounds ({} chars), summarized {} older rounds into {} chars",
				conversationId, roundsToKeep.size(), keptChars, roundsToSummarize.size(),
				summaryMessage != null ? summaryMessage.getText().length() : 0);
	}

	/**
	 * Group messages into dialog rounds (UserMessage + AssistantMessage pairs).
	 * @param messages List of messages
	 * @return List of dialog rounds
	 */
	private List<DialogRound> groupMessagesIntoRounds(List<Message> messages) {
		List<DialogRound> rounds = new ArrayList<>();
		DialogRound currentRound = null;

		for (Message message : messages) {
			if (message instanceof UserMessage) {
				// Start a new round
				if (currentRound != null) {
					rounds.add(currentRound);
				}
				currentRound = new DialogRound();
				currentRound.addMessage(message);
			}
			else if (message instanceof AssistantMessage) {
				// Add to current round
				if (currentRound == null) {
					// If no user message before, create a new round
					currentRound = new DialogRound();
				}
				currentRound.addMessage(message);
				// Round is complete (User + Assistant), add it
				rounds.add(currentRound);
				currentRound = null;
			}
			else {
				// Other message types, add to current round if exists
				if (currentRound != null) {
					currentRound.addMessage(message);
				}
			}
		}

		// Add the last round if it exists and wasn't completed
		if (currentRound != null) {
			rounds.add(currentRound);
		}

		return rounds;
	}

	/**
	 * Summarize multiple dialog rounds into a single UserMessage of 3000-4000 chars.
	 * @param rounds Dialog rounds to summarize
	 * @return Summarized UserMessage
	 */
	private UserMessage summarizeRounds(List<DialogRound> rounds) {
		try {
			// Build conversation text from rounds
			StringBuilder conversationText = new StringBuilder();
			for (DialogRound round : rounds) {
				for (Message message : round.getMessages()) {
					String content = extractMessageContent(message);
					if (message instanceof UserMessage) {
						conversationText.append("User: ").append(content).append("\n\n");
					}
					else if (message instanceof AssistantMessage) {
						conversationText.append("Assistant: ").append(content).append("\n\n");
					}
				}
			}

			String conversationHistory = conversationText.toString();

			// Create summarization prompt
			String summaryPrompt = String.format("""
					Please summarize the following conversation history into a concise summary.
					The summary should be between %d and %d characters.
					Preserve key information, decisions, and important details.
					Format the summary as a clear narrative of what happened in the conversation.

					Conversation history:
					%s
					""", SUMMARY_MIN_CHARS, SUMMARY_MAX_CHARS, conversationHistory);

			// Use LLM to generate summary
			ChatClient chatClient = llmService.getDefaultDynamicAgentChatClient();
			ChatResponse response = chatClient.prompt()
				.system("You are a helpful assistant that summarizes conversations concisely and accurately.")
				.user(summaryPrompt)
				.call()
				.chatResponse();

			String summary = response.getResult().getOutput().getText();

			// Ensure summary is within target range
			if (summary.length() < SUMMARY_MIN_CHARS) {
				log.warn("Generated summary is too short ({} chars), expanding...", summary.length());
				// Could add a follow-up prompt to expand, but for now just use as-is
			}
			else if (summary.length() > SUMMARY_MAX_CHARS) {
				log.warn("Generated summary is too long ({} chars), truncating...", summary.length());
				summary = summary.substring(0, SUMMARY_MAX_CHARS);
			}

			return new UserMessage(summary);

		}
		catch (Exception e) {
			log.error("Failed to summarize dialog rounds", e);
			// Fallback: create a simple summary
			String fallbackSummary = String.format(
					"Previous conversation history (%d dialog rounds) has been summarized due to length constraints.",
					rounds.size());
			return new UserMessage(fallbackSummary);
		}
	}

	/**
	 * Inner class to represent a dialog round (UserMessage + AssistantMessage pair).
	 */
	private static class DialogRound {

		private final List<Message> messages = new ArrayList<>();

		public void addMessage(Message message) {
			messages.add(message);
		}

		public List<Message> getMessages() {
			return messages;
		}

		public int getTotalChars() {
			return messages.stream().mapToInt(msg -> {
				String text = msg.getText();
				return text != null ? text.length() : 0;
			}).sum();
		}

	}

	/**
	 * Get the configured maximum character count from ManusProperties.
	 * @return Maximum character count
	 */
	public int getMaxCharacterCount() {
		return manusProperties != null ? manusProperties.getConversationMemoryMaxChars() : 30000;
	}

}
