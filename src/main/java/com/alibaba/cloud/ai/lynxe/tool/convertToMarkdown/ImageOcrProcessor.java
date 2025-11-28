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
package com.alibaba.cloud.ai.lynxe.tool.convertToMarkdown;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.MimeTypeUtils;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.runtime.executor.ImageRecognitionExecutorPool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;

/**
 * Image OCR Processor using OpenAI Image Model
 *
 * Converts image files (JPG, PNG, GIF) to text using OCR processing with OpenAI's image
 * models. This processor handles various image formats and uses OCR to extract text
 * content.
 */
public class ImageOcrProcessor {

	private static final Logger log = LoggerFactory.getLogger(ImageOcrProcessor.class);

	private final UnifiedDirectoryManager directoryManager;

	private final LlmService llmService;

	private final LynxeProperties lynxeProperties;

	private final ImageRecognitionExecutorPool imageRecognitionExecutorPool;

	// Image format name for consistency across all image operations
	private String imageFormatName = "JPEG";

	public ImageOcrProcessor(UnifiedDirectoryManager directoryManager, LlmService llmService,
			LynxeProperties lynxeProperties, ImageRecognitionExecutorPool imageRecognitionExecutorPool) {
		this.directoryManager = directoryManager;
		this.llmService = llmService;
		this.lynxeProperties = lynxeProperties;
		this.imageRecognitionExecutorPool = imageRecognitionExecutorPool;
		log.info("ImageOcrProcessor initialized with all dependencies");
	}

	/**
	 * Convert image file to text using OCR processing
	 * @param sourceFile The source image file
	 * @param additionalRequirement Optional additional requirements for OCR processing
	 * @param currentPlanId Current plan ID for file operations
	 * @param targetFilename Optional target filename (if null, will generate _ocr.txt
	 * filename)
	 * @return ToolExecuteResult with OCR processing status and extracted text
	 */
	public ToolExecuteResult convertImageToTextWithOcr(Path sourceFile, String additionalRequirement,
			String currentPlanId, String targetFilename) {
		try {
			log.info("Starting OCR processing for image file: {}", sourceFile.getFileName());

			// Step 1: Check if OCR result already exists
			String originalFilename = sourceFile.getFileName().toString();
			String ocrFilename = (targetFilename != null) ? targetFilename : generateOcrFilename(originalFilename);
			if (ocrFileExists(currentPlanId, ocrFilename)) {
				log.info("OCR result file already exists, skipping processing: {}", ocrFilename);
				return new ToolExecuteResult("Skipped OCR processing - result file already exists: " + ocrFilename);
			}

			// Step 2: Load and validate the image
			BufferedImage image = loadImage(sourceFile);
			if (image == null) {
				return new ToolExecuteResult("Error: Could not load image file or unsupported format");
			}

			// Step 3: Process image with OCR using executor pool
			String extractedText;
			if (imageRecognitionExecutorPool != null) {
				CompletableFuture<String> ocrTask = imageRecognitionExecutorPool.submitTask(() -> {
					log.info("Processing image with OCR");
					return processImageWithOcrWithRetry(image, 1);
				});

				try {
					extractedText = ocrTask.get();
				}
				catch (InterruptedException | ExecutionException e) {
					log.error("Error processing image with OCR: {}", e.getMessage());
					return new ToolExecuteResult("Error: OCR processing failed - " + e.getMessage());
				}
			}
			else {
				// Throw exception if executor pool is not available
				String errorMessage = "ImageRecognitionExecutorPool not available - OCR processing cannot continue";
				log.error(errorMessage);
				throw new IllegalStateException(errorMessage);
			}

			if (extractedText == null || extractedText.trim().isEmpty()) {
				return new ToolExecuteResult("Error: No text could be extracted from the image using OCR");
			}

			// Step 4: Format content based on target filename
			StringBuilder formattedText = new StringBuilder();
			if (targetFilename != null && targetFilename.endsWith(".md")) {
				// Markdown output: add simple header and the extracted text
				formattedText.append("# OCR Result\n\n");
				formattedText.append("**Source File**: `").append(originalFilename).append("`\n\n");
				formattedText.append("---\n\n");
				formattedText.append(extractedText);
				formattedText.append("\n");
			}
			else {
				// Plain text output
				formattedText.append(extractedText);
			}

			// Step 5: Save OCR result
			Path outputFile = saveOcrResult(formattedText.toString(), ocrFilename, currentPlanId);
			if (outputFile == null) {
				return new ToolExecuteResult("Error: Failed to save OCR result file");
			}

			// Step 6: Return success result
			String result;
			if (targetFilename != null && targetFilename.endsWith(".md")) {
				result = String.format("Successfully converted image file to Markdown using OCR\n\n"
						+ "**Output File**: %s\n\n" + "**Processing Method**: OCR with OpenAI Image Model\n\n",
						ocrFilename);
			}
			else {
				result = String.format("Successfully processed image with OCR\n\n" + "**Output File**: %s\n\n"
						+ "**Processing Method**: OCR with OpenAI Image Model\n\n", ocrFilename);
			}

			// Add content preview if less than 1000 characters
			String content = extractedText;
			if (content.length() < 1000) {
				result += "**Content Preview**:\n\n" + content;
			}
			else {
				result += "**Content Preview**:\n\n" + content.substring(0, 1000)
						+ "...\n\n*[Content truncated - see full result in output file]*";
			}

			log.info("Image OCR processing completed: {} -> {}", originalFilename, ocrFilename);
			return new ToolExecuteResult(result);

		}
		catch (Exception e) {
			log.error("Error processing image with OCR: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * Convert image file to text using OCR processing (backward compatibility method)
	 * @param sourceFile The source image file
	 * @param additionalRequirement Optional additional requirements for OCR processing
	 * @param currentPlanId Current plan ID for file operations
	 * @return ToolExecuteResult with OCR processing status and extracted text
	 */
	public ToolExecuteResult convertImageToTextWithOcr(Path sourceFile, String additionalRequirement,
			String currentPlanId) {
		return convertImageToTextWithOcr(sourceFile, additionalRequirement, currentPlanId, null);
	}

	/**
	 * Load image from file and validate format
	 * @param sourceFile The source image file
	 * @return BufferedImage or null if failed
	 */
	private BufferedImage loadImage(Path sourceFile) {
		try {
			BufferedImage image = ImageIO.read(sourceFile.toFile());
			if (image == null) {
				log.error("Could not load image: {}", sourceFile.getFileName());
				return null;
			}

			// Optimize the image for OCR processing
			BufferedImage optimizedImage = optimizeImageForOcr(image);
			log.debug("Loaded and optimized image: {} ({}x{})", sourceFile.getFileName(), optimizedImage.getWidth(),
					optimizedImage.getHeight());

			return optimizedImage;
		}
		catch (IOException e) {
			log.error("Error loading image: {}", sourceFile.getFileName(), e);
			return null;
		}
	}

	/**
	 * Process a single image with OCR using ChatClient with retry mechanism
	 * @param image The BufferedImage to process
	 * @param imageNumber The image number for logging
	 * @return Extracted text or null if failed
	 */
	private String processImageWithOcrWithRetry(BufferedImage image, int imageNumber) {
		int maxRetryAttempts = getConfiguredMaxRetryAttempts();

		for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
			try {
				log.debug("OCR attempt {} of {} for image {}", attempt, maxRetryAttempts, imageNumber);
				String result = processImageWithOcr(image, imageNumber);

				if (result != null && !result.trim().isEmpty()) {
					if (attempt > 1) {
						log.info("OCR succeeded on attempt {} for image {}", attempt, imageNumber);
					}
					return result;
				}

				log.warn("OCR attempt {} failed for image {} - empty result", attempt, imageNumber);

			}
			catch (Exception e) {
				log.warn("OCR attempt {} failed for image {}: {}", attempt, imageNumber, e.getMessage());

				// If this is the last attempt, log the error
				if (attempt == maxRetryAttempts) {
					log.error("All {} OCR attempts failed for image {}", maxRetryAttempts, imageNumber, e);
				}
			}

			// Add a small delay between retry attempts (except for the last attempt)
			if (attempt < maxRetryAttempts) {
				try {
					Thread.sleep(1000); // 1 second delay between retries
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.warn("OCR retry interrupted for image {}", imageNumber);
					break;
				}
			}
		}

		log.error("All OCR attempts failed for image {}", imageNumber);
		return null;
	}

	/**
	 * Process a single image with OCR using ChatClient
	 */
	private String processImageWithOcr(BufferedImage image, int imageNumber) {
		if (llmService == null) {
			log.error("LlmService is not initialized, cannot perform OCR");
			return null;
		}

		try {
			// Convert BufferedImage to InputStream for ChatClient using configured format
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, imageFormatName, baos);
			byte[] imageBytes = baos.toByteArray();

			// Create InputStream from byte array
			java.io.InputStream imageInputStream = new java.io.ByteArrayInputStream(imageBytes);

			log.debug("Processing image for OCR ({} bytes, format: {})", imageBytes.length, imageFormatName);

			// Get the ChatClient from LlmService
			ChatClient chatClient = llmService.getDefaultDynamicAgentChatClient();
			// Use configured model name from LynxeProperties
			String modelName = getConfiguredModelName();
			ChatOptions chatOptions = ChatOptions.builder().model(modelName).build();
			// Use ChatClient to process the image with OCR
			String extractedText = chatClient.prompt()
				.options(chatOptions)
				.system(systemMessage -> systemMessage
					.text("You are an OCR (Optical Character Recognition) specialist.")
					.text("Extract all visible text content from the provided image.")
					.text("Return only the extracted text without any additional formatting or descriptions.")
					.text("Preserve the structure and layout of the text as much as possible.")
					.text("Focus on accurate text recognition and maintain readability.")
					.text("If no text is visible in the image, return ''(empty string) "))
				.user(userMessage -> userMessage.text("Please extract all text content from this image:")
					.media(MimeTypeUtils.parseMimeType("image/" + imageFormatName.toLowerCase()),
							new InputStreamResource(imageInputStream)))
				.call()
				.content();

			if (extractedText != null && !extractedText.trim().isEmpty()
					&& !extractedText.toLowerCase().contains("no text detected")) {
				log.debug("Successfully extracted text from image with OCR: {} characters", extractedText.length());
				return extractedText;
			}
			else {
				log.warn("No text extracted from image - empty or no text response");
				return null;
			}

		}
		catch (Exception e) {
			log.error("Error processing image with OCR using ChatClient: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Check if OCR result file already exists
	 */
	private boolean ocrFileExists(String currentPlanId, String ocrFilename) {
		try {
			Path rootPlanDir = directoryManager.getRootPlanDirectory(currentPlanId);
			Path ocrFile = rootPlanDir.resolve(ocrFilename);
			return Files.exists(ocrFile);
		}
		catch (Exception e) {
			log.error("Error checking if OCR file exists: {}", ocrFilename, e);
			return false;
		}
	}

	/**
	 * Generate OCR result filename by replacing extension with _ocr.txt
	 */
	private String generateOcrFilename(String originalFilename) {
		int lastDotIndex = originalFilename.lastIndexOf('.');
		if (lastDotIndex > 0) {
			return originalFilename.substring(0, lastDotIndex) + "_ocr.txt";
		}
		return originalFilename + "_ocr.txt";
	}

	/**
	 * Save OCR result to file
	 */
	private Path saveOcrResult(String content, String filename, String currentPlanId) {
		try {
			Path rootPlanDir = directoryManager.getRootPlanDirectory(currentPlanId);
			Path outputFile = rootPlanDir.resolve(filename);

			Files.write(outputFile, content.getBytes("UTF-8"), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);

			log.info("OCR result file saved: {}", outputFile);
			return outputFile;
		}
		catch (IOException e) {
			log.error("Error saving OCR result file: {}", filename, e);
			return null;
		}
	}

	/**
	 * Set the image format name for all image operations
	 * @param imageFormatName The image format name (e.g., "JPEG", "PNG", "BMP")
	 */
	public void setImageFormatName(String imageFormatName) {
		this.imageFormatName = imageFormatName;
		log.info("Image format changed to: {}", imageFormatName);
	}

	/**
	 * Get the current image format name
	 * @return Current image format name
	 */
	public String getImageFormatName() {
		return imageFormatName;
	}

	/**
	 * Get current image format status with detailed information
	 * @return Status string describing current image format configuration
	 */
	public String getImageFormatStatus() {
		return String.format("Image format: %s - Used for OCR processing and file saving", imageFormatName);
	}

	/**
	 * Optimize image for OCR processing by reducing size and improving quality
	 * @param originalImage The original BufferedImage
	 * @return Optimized BufferedImage for OCR
	 */
	private BufferedImage optimizeImageForOcr(BufferedImage originalImage) {
		int originalWidth = originalImage.getWidth();
		int originalHeight = originalImage.getHeight();

		// Calculate optimized dimensions (max 2000px on longest side for OCR)
		int maxDimension = 2000;
		int newWidth = originalWidth;
		int newHeight = originalHeight;

		if (originalWidth > maxDimension || originalHeight > maxDimension) {
			if (originalWidth > originalHeight) {
				newWidth = maxDimension;
				newHeight = (int) ((float) originalHeight * maxDimension / originalWidth);
			}
			else {
				newHeight = maxDimension;
				newWidth = (int) ((float) originalWidth * maxDimension / originalHeight);
			}
		}

		// Only resize if necessary
		if (newWidth != originalWidth || newHeight != originalHeight) {
			log.debug("Resizing image from {}x{} to {}x{} for OCR optimization", originalWidth, originalHeight,
					newWidth, newHeight);

			BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g2d = resizedImage.createGraphics();

			// Use high-quality rendering hints for better OCR results
			g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
					java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
			g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
			g2d.dispose();

			return resizedImage;
		}

		return originalImage;
	}

	/**
	 * Get configured model name from LynxeProperties
	 * @return configured model name or default value
	 */
	private String getConfiguredModelName() {
		if (lynxeProperties != null) {
			String configuredModelName = lynxeProperties.getImageRecognitionModelName();
			if (configuredModelName != null && !configuredModelName.trim().isEmpty()) {
				return configuredModelName;
			}
		}
		return "qwen-vl-ocr-latest"; // Default model name
	}

	/**
	 * Get configured max retry attempts from LynxeProperties
	 * @return configured max retry attempts or default value
	 */
	private int getConfiguredMaxRetryAttempts() {
		if (lynxeProperties != null) {
			Integer configuredMaxRetryAttempts = lynxeProperties.getImageRecognitionMaxRetryAttempts();
			if (configuredMaxRetryAttempts != null && configuredMaxRetryAttempts > 0) {
				return configuredMaxRetryAttempts;
			}
		}
		return 3; // Default max retry attempts
	}

	/**
	 * Get current OCR processor configuration status
	 * @return Status string describing current OCR configuration
	 */
	public String getOcrConfigurationStatus() {
		StringBuilder status = new StringBuilder();
		status.append("Image OCR Processor Configuration:\n");
		status.append("- Model: ").append(getConfiguredModelName()).append("\n");
		status.append("- Image Format: ").append(imageFormatName).append("\n");
		status.append("- Max Retry Attempts: ").append(getConfiguredMaxRetryAttempts()).append("\n");
		status.append("- Executor Pool Size: ")
			.append(imageRecognitionExecutorPool != null
					? String.valueOf(imageRecognitionExecutorPool.getCurrentPoolSize()) : "Not Available")
			.append("\n");
		return status.toString();
	}

}
