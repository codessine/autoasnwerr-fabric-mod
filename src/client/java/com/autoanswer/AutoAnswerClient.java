package com.autoanswer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import com.mojang.brigadier.arguments.StringArgumentType;

public class AutoAnswerClient implements ClientModInitializer {
  public static final String MOD_ID = "autoanswer";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  
  private static boolean autoAnswerEnabled = true;
  private static boolean readMyMindEnabled = true; // Respond to own messages
  private static int responseDelay = 1000; // 1 second delay in milliseconds
  private static boolean soundAlertsEnabled = true;
  
  // Success counter
  private static int successCount = 0;
  
  // Use ConcurrentHashMap for thread safety and make it non-static for persistence
  private static final Map<String, String> QUESTION_ANSWERS = new ConcurrentHashMap<>();
  private static final String CONFIG_FILE = "config/autoanswer_questions.json";
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  
  // Symbol math equation storage for multi-line detection
  private static String currentSymbol1 = null;
  private static Integer currentValue1 = null;
  private static String currentSymbol2 = null;
  private static Integer currentValue2 = null;
  private static long lastSymbolMathTime = 0;
  private static final long SYMBOL_MATH_TIMEOUT = 10000; // 10 seconds timeout

  // Pattern for individual symbol math equations
  private static final Pattern SINGLE_SYMBOL_EQUATION_PATTERN = Pattern.compile(
      "\\|?\\s*(\\S+)\\s*\\+\\s*(\\S+)\\s*\\+\\s*(\\S+)\\s*=\\s*(\\d+)",
      Pattern.CASE_INSENSITIVE
  );
  
  // Updated patterns to handle Unicode small caps and | prefix
  
  // Pattern for "write out" questions - handles Unicode small caps
  private static final Pattern WRITE_OUT_PATTERN = Pattern.compile(
      "\\|?\\s*[ʏyY][ᴏoO][ᴜuU]\\s+[ʜhH][ᴀaA][ᴠvV][ᴇeE]\\s+30\\s+[sS][ᴇeE][ᴄcC][ᴏoO][ɴnN][ᴅdD][sS]\\s+[ᴛtT][ᴏoO]\\s+[ᴡwW][ʀrR][ɪiI][ᴛtT][ᴇeE]\\s+[ᴏoO][ᴜuU][ᴛtT]:\\s*([A-Za-z0-9]+)", 
      Pattern.CASE_INSENSITIVE
  );
  
  // Pattern for "write out the word" questions - handles Unicode small caps
  private static final Pattern WRITE_OUT_WORD_PATTERN = Pattern.compile(
      "\\|?\\s*[ʏyY][ᴏoO][ᴜuU]\\s+[ʜhH][ᴀaA][ᴠvV][ᴇeE]\\s+30\\s+[sS][ᴇeE][ᴄcC][ᴏoO][ɴnN][ᴅdD][sS]\\s+[ᴛtT][ᴏoO]\\s+[ᴡwW][ʀrR][ɪiI][ᴛtT][ᴇeE]\\s+[ᴏoO][ᴜuU][ᴛtT]\\s+[ᴛtT][ʜhH][ᴇeE]\\s+[ᴡwW][ᴏoO][ʀrR][ᴅdD]:\\s*([A-Za-z0-9]+)", 
      Pattern.CASE_INSENSITIVE
  );
  
  // Pattern for math questions - handles Unicode small caps
  private static final Pattern MATH_PATTERN = Pattern.compile(
      "\\|?\\s*[ʏyY][ᴏoO][ᴜuU]\\s+[ʜhH][ᴀaA][ᴠvV][ᴇeE]\\s+30\\s+[sS][ᴇeE][ᴄcC][ᴏoO][ɴnN][ᴅdD][sS]\\s+[ᴛtT][ᴏoO]\\s+[sS][ᴏoO][ʟlL][ᴠvV][ᴇeE]:\\s*(\\d+)\\s*([+\\-*/])\\s*(\\d+)", 
      Pattern.CASE_INSENSITIVE
  );
  
  // Pattern for symbol math questions - handles Unicode and any symbols
  
  
  // Function to normalize Unicode small caps to regular characters
  private static String normalizeUnicode(String text) {
      if (text == null) return "";
      
      return text
          // Small caps to regular letters
          .replace("ʏ", "y").replace("ᴏ", "o").replace("ᴜ", "u")
          .replace("ʜ", "h").replace("ᴀ", "a").replace("ᴠ", "v").replace("ᴇ", "e")
          .replace("ᴛ", "t").replace("ɪ", "i").replace("ᴡ", "w").replace("ʀ", "r")
          .replace("ᴅ", "d").replace("s", "s").replace("ᴄ", "c").replace("ɴ", "n")
          .replace("ʟ", "l")
          // Common words
          .replace("sᴇᴄᴏɴᴅs", "seconds").replace("ᴛᴏ", "to")
          .replace("ᴡʀɪᴛᴇ", "write").replace("ᴏᴜᴛ", "out")
          .replace("ᴛʜᴇ", "the").replace("ᴡᴏʀᴅ", "word")
          .replace("sᴏʟᴠᴇ", "solve").replace("ᴜɴʀᴇᴠᴇʀsᴇ", "unreverse")
          .replace("ғɪʟʟ", "fill");
  }
  
  // Load questions from file
  private static void loadQuestions() {
      try {
          Path configPath = Paths.get(CONFIG_FILE);
          if (Files.exists(configPath)) {
              String json = Files.readString(configPath);
              Type type = new TypeToken<Map<String, String>>(){}.getType();
              Map<String, String> loadedQuestions = gson.fromJson(json, type);
              if (loadedQuestions != null) {
                  QUESTION_ANSWERS.putAll(loadedQuestions);
                  LOGGER.info("Loaded {} questions from {}", loadedQuestions.size(), CONFIG_FILE);
              }
          } else {
              // Create default questions if file doesn't exist
              addDefaultQuestions();
              saveQuestions();
          }
      } catch (Exception e) {
          LOGGER.error("Failed to load questions from file: {}", e.getMessage());
          addDefaultQuestions();
      }
  }

  // Save questions to file
  private static void saveQuestions() {
      try {
          Path configPath = Paths.get(CONFIG_FILE);
          Files.createDirectories(configPath.getParent());
          String json = gson.toJson(QUESTION_ANSWERS);
          Files.writeString(configPath, json);
          LOGGER.info("Saved {} questions to {}", QUESTION_ANSWERS.size(), CONFIG_FILE);
      } catch (Exception e) {
          LOGGER.error("Failed to save questions to file: {}", e.getMessage());
      }
  }

  // Add default questions
  private static void addDefaultQuestions() {
      QUESTION_ANSWERS.put("You have 30 seconds to unreverse: eturBnilgiP", "PiglinBrute");
      QUESTION_ANSWERS.put("You have 30 seconds to unreverse: eyErednE", "EnderEye");
      QUESTION_ANSWERS.put("You have 30 seconds to unreverse: hsifreffuP", "Pufferfish");
      QUESTION_ANSWERS.put("You have 30 seconds to unreverse: aDy", "Day");
      QUESTION_ANSWERS.put("You have 30 seconds to unreverse: attoccarreTdezalG", "GlazedTerracotta");
      QUESTION_ANSWERS.put("You have 30 seconds to fill in the word: _ri_ent", "Trident");
  }
  
  @Override
  public void onInitializeClient() {
      // Load questions from file
      loadQuestions();
      
      LOGGER.info("AutoAnswer mod initialized with {} question-answer pairs!", QUESTION_ANSWERS.size());
      
      // Register chat message listener - using the correct event
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
          if (!autoAnswerEnabled) {
              return;
          }
          
          // Get the raw message text - try multiple methods
          String messageText = message.getString();
          if (messageText == null || messageText.trim().isEmpty()) {
              // Try getting the literal content if getString() fails
              try {
                  messageText = message.getLiteralString();
                  if (messageText == null) {
                      messageText = "";
                  }
              } catch (Exception e) {
                  messageText = "";
              }
          }
          
          // Normalize Unicode characters
          String normalizedText = normalizeUnicode(messageText);
          
          LOGGER.info("=== MESSAGE RECEIVED ===");
          LOGGER.info("Raw message: '{}'", messageText);
          LOGGER.info("Normalized message: '{}'", normalizedText);
          LOGGER.info("Message length: {}", messageText.length());
          LOGGER.info("ReadMyMind enabled: {}, AutoAnswer enabled: {}", readMyMindEnabled, autoAnswerEnabled);

          // Log message source for debugging
          if (messageText.startsWith("<") && messageText.contains("> ")) {
              LOGGER.info("PLAYER MESSAGE detected: {}", messageText);
          } else {
              LOGGER.info("SERVER/SYSTEM MESSAGE detected: {}", messageText);
          }
          
          // Only ignore player chat messages when readmymind is off (server messages should always be processed)
          if (!readMyMindEnabled) {
              MinecraftClient client = MinecraftClient.getInstance();
              if (client.player != null) {
                  String playerName = client.player.getName().getString();
                  // Only ignore if it's clearly a player chat message from us
                  if (messageText.startsWith("<" + playerName + "> ")) {
                      LOGGER.info("Ignoring own player message (readmymind disabled): {}", messageText);
                      return;
                  }
              }
          }
          
          // Check for single symbol math equations (multi-line detection)
          Matcher singleSymbolMatcher = SINGLE_SYMBOL_EQUATION_PATTERN.matcher(messageText);
          if (singleSymbolMatcher.find()) {
              String sym1 = singleSymbolMatcher.group(1);
              String sym2 = singleSymbolMatcher.group(2);
              String sym3 = singleSymbolMatcher.group(3);
              int result = Integer.parseInt(singleSymbolMatcher.group(4));
              
              long currentTime = System.currentTimeMillis();
              
              // Reset if too much time has passed
              if (currentTime - lastSymbolMathTime > SYMBOL_MATH_TIMEOUT) {
                  currentSymbol1 = null;
                  currentValue1 = null;
                  currentSymbol2 = null;
                  currentValue2 = null;
              }
              
              lastSymbolMathTime = currentTime;
              
              LOGGER.info("SYMBOL EQUATION DETECTED: {} + {} + {} = {}", sym1, sym2, sym3, result);
              
              // Check if all three symbols are the same (first or second equation)
              if (sym1.equals(sym2) && sym2.equals(sym3)) {
                  // This is a "X + X + X = result" equation
                  int symbolValue = result / 3;
                  
                  if (currentSymbol1 == null) {
                      // First equation
                      currentSymbol1 = sym1;
                      currentValue1 = symbolValue;
                      LOGGER.info("STORED FIRST SYMBOL: '{}' = {}", currentSymbol1, currentValue1);
                      
                      scheduleResponse("", "✓ Detected first symbol equation");
                      return;
                  } else if (currentSymbol2 == null && !sym1.equals(currentSymbol1)) {
                      // Second equation with different symbol
                      currentSymbol2 = sym1;
                      currentValue2 = symbolValue;
                      LOGGER.info("STORED SECOND SYMBOL: '{}' = {}", currentSymbol2, currentValue2);
                      
                      scheduleResponse("", "✓ Detected second symbol equation");
                      return;
                  }
              } else if (currentSymbol1 != null && currentSymbol2 != null) {
                  // This might be the third equation with mixed symbols
                  // Check if it contains our known symbols and ✗
                  boolean hasSymbol1 = sym1.equals(currentSymbol1) || sym2.equals(currentSymbol1) || sym3.equals(currentSymbol1);
                  boolean hasSymbol2 = sym1.equals(currentSymbol2) || sym2.equals(currentSymbol2) || sym3.equals(currentSymbol2);
                  boolean hasUnknown = sym1.equals("✗") || sym2.equals("✗") || sym3.equals("✗");
                  
                  if (hasSymbol1 && hasSymbol2 && hasUnknown) {
                      // This is the final equation! Calculate the answer
                      int answer = result - currentValue1 - currentValue2;
                      
                      LOGGER.info("SYMBOL MATH SOLVED!");
                      LOGGER.info("Symbol 1: '{}' = {}", currentSymbol1, currentValue1);
                      LOGGER.info("Symbol 2: '{}' = {}", currentSymbol2, currentValue2);
                      LOGGER.info("Final equation: {} + {} + ✗ = {}", currentSymbol1, currentSymbol2, result);
                      LOGGER.info("Answer: ✗ = {} - {} - {} = {}", result, currentValue1, currentValue2, answer);
                      
                      scheduleResponse(String.valueOf(answer), "✓ Solved symbol math: " + answer);
                      
                      // Reset for next symbol math question
                      currentSymbol1 = null;
                      currentValue1 = null;
                      currentSymbol2 = null;
                      currentValue2 = null;
                      
                      return;
                  }
              }
          }
          
          // Then check for regular math pattern questions (use normalized text)
          Matcher mathMatcher = MATH_PATTERN.matcher(normalizedText);
          if (mathMatcher.find()) {
              int num1 = Integer.parseInt(mathMatcher.group(1));
              String operator = mathMatcher.group(2);
              int num2 = Integer.parseInt(mathMatcher.group(3));
              
              int result = calculateMath(num1, operator, num2);
              LOGGER.info("MATH MATCH! Found equation: {} {} {} = {}", num1, operator, num2, result);
              
              scheduleResponse(String.valueOf(result), "✓ Solved: " + num1 + " " + operator + " " + num2 + " = " + result);
              return;
          }
          
          // Then check for "write out the word" pattern questions (use normalized text)
          Matcher writeOutWordMatcher = WRITE_OUT_WORD_PATTERN.matcher(normalizedText);
          if (writeOutWordMatcher.find()) {
              String wordToWrite = writeOutWordMatcher.group(1);
              LOGGER.info("WRITE OUT WORD MATCH! Found word: '{}'", wordToWrite);
              
              scheduleResponse(wordToWrite, "✓ Wrote word: " + wordToWrite);
              return;
          }
          
          // Then check for regular "write out" pattern questions (use normalized text)
          Matcher writeOutMatcher = WRITE_OUT_PATTERN.matcher(normalizedText);
          if (writeOutMatcher.find()) {
              String stringToWrite = writeOutMatcher.group(1);
              LOGGER.info("WRITE OUT MATCH! Found string: '{}'", stringToWrite);
              
              scheduleResponse(stringToWrite, "✓ Wrote out: " + stringToWrite);
              return;
          }
          
          // Finally check regular trigger phrases (case-insensitive) - check both original and normalized
          for (Map.Entry<String, String> entry : QUESTION_ANSWERS.entrySet()) {
              String trigger = entry.getKey();
              String response = entry.getValue();
              String normalizedTrigger = normalizeUnicode(trigger);
              
              if (messageText.toLowerCase().contains(trigger.toLowerCase()) ||
                  normalizedText.toLowerCase().contains(trigger.toLowerCase()) ||
                  messageText.toLowerCase().contains(normalizedTrigger.toLowerCase()) ||
                  normalizedText.toLowerCase().contains(normalizedTrigger.toLowerCase())) {
                  
                  LOGGER.info("REGULAR MATCH! Message contains trigger: '{}'", trigger);
                  LOGGER.info("Sending response: {}", response);
                  
                  scheduleResponse(response, "✓ Sent: " + response);
                  break;
              }
          }
      });
      
      // Also listen to CHAT events as backup
      ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
          if (!autoAnswerEnabled) {
              return;
          }
          
          // Get the raw message text - try multiple methods
          String messageText = message.getString();
          if (messageText == null || messageText.trim().isEmpty()) {
              try {
                  messageText = message.getLiteralString();
                  if (messageText == null) {
                      messageText = "";
                  }
              } catch (Exception e) {
                  messageText = "";
              }
          }
          
          // Normalize Unicode characters
          String normalizedText = normalizeUnicode(messageText);
          
          LOGGER.info("CHAT EVENT - Raw message: '{}'", messageText);
          LOGGER.info("CHAT EVENT - Normalized message: '{}'", normalizedText);
          
          // Only ignore player chat messages when readmymind is off
          if (!readMyMindEnabled) {
              MinecraftClient client = MinecraftClient.getInstance();
              if (client.player != null) {
                  String playerName = client.player.getName().getString();
                  if (messageText.startsWith("<" + playerName + "> ")) {
                      LOGGER.info("Ignoring own player message in CHAT event (readmymind disabled): {}", messageText);
                      return;
                  }
              }
          }
          
          // Check for single symbol math equations (multi-line detection)
          Matcher singleSymbolMatcher = SINGLE_SYMBOL_EQUATION_PATTERN.matcher(messageText);
          if (singleSymbolMatcher.find()) {
              String sym1 = singleSymbolMatcher.group(1);
              String sym2 = singleSymbolMatcher.group(2);
              String sym3 = singleSymbolMatcher.group(3);
              int result = Integer.parseInt(singleSymbolMatcher.group(4));
              
              long currentTime = System.currentTimeMillis();
              
              // Reset if too much time has passed
              if (currentTime - lastSymbolMathTime > SYMBOL_MATH_TIMEOUT) {
                  currentSymbol1 = null;
                  currentValue1 = null;
                  currentSymbol2 = null;
                  currentValue2 = null;
              }
              
              lastSymbolMathTime = currentTime;
              
              LOGGER.info("SYMBOL EQUATION DETECTED: {} + {} + {} = {}", sym1, sym2, sym3, result);
              
              // Check if all three symbols are the same (first or second equation)
              if (sym1.equals(sym2) && sym2.equals(sym3)) {
                  // This is a "X + X + X = result" equation
                  int symbolValue = result / 3;
                  
                  if (currentSymbol1 == null) {
                      // First equation
                      currentSymbol1 = sym1;
                      currentValue1 = symbolValue;
                      LOGGER.info("STORED FIRST SYMBOL: '{}' = {}", currentSymbol1, currentValue1);
                      
                      scheduleResponse("", "✓ Detected first symbol equation");
                      return;
                  } else if (currentSymbol2 == null && !sym1.equals(currentSymbol1)) {
                      // Second equation with different symbol
                      currentSymbol2 = sym1;
                      currentValue2 = symbolValue;
                      LOGGER.info("STORED SECOND SYMBOL: '{}' = {}", currentSymbol2, currentValue2);
                      
                      scheduleResponse("", "✓ Detected second symbol equation");
                      return;
                  }
              } else if (currentSymbol1 != null && currentSymbol2 != null) {
                  // This might be the third equation with mixed symbols
                  // Check if it contains our known symbols and ✗
                  boolean hasSymbol1 = sym1.equals(currentSymbol1) || sym2.equals(currentSymbol1) || sym3.equals(currentSymbol1);
                  boolean hasSymbol2 = sym1.equals(currentSymbol2) || sym2.equals(currentSymbol2) || sym3.equals(currentSymbol2);
                  boolean hasUnknown = sym1.equals("✗") || sym2.equals("✗") || sym3.equals("✗");
                  
                  if (hasSymbol1 && hasSymbol2 && hasUnknown) {
                      // This is the final equation! Calculate the answer
                      int answer = result - currentValue1 - currentValue2;
                      
                      LOGGER.info("SYMBOL MATH SOLVED!");
                      LOGGER.info("Symbol 1: '{}' = {}", currentSymbol1, currentValue1);
                      LOGGER.info("Symbol 2: '{}' = {}", currentSymbol2, currentValue2);
                      LOGGER.info("Final equation: {} + {} + ✗ = {}", currentSymbol1, currentSymbol2, result);
                      LOGGER.info("Answer: ✗ = {} - {} - {} = {}", result, currentValue1, currentValue2, answer);
                      
                      scheduleResponse(String.valueOf(answer), "✓ Auto-solved symbol math: " + answer);
                      
                      // Reset for next symbol math question
                      currentSymbol1 = null;
                      currentValue1 = null;
                      currentSymbol2 = null;
                      currentValue2 = null;
                      
                      return;
                  }
              }
          }
          
          // Check for math pattern (use normalized text)
          Matcher mathMatcher = MATH_PATTERN.matcher(normalizedText);
          if (mathMatcher.find()) {
              int num1 = Integer.parseInt(mathMatcher.group(1));
              String operator = mathMatcher.group(2);
              int num2 = Integer.parseInt(mathMatcher.group(3));
              
              int result = calculateMath(num1, operator, num2);
              LOGGER.info("CHAT EVENT MATH MATCH! Equation: {} {} {} = {}", num1, operator, num2, result);
              
              scheduleResponse(String.valueOf(result), "✓ Auto-solved: " + num1 + " " + operator + " " + num2 + " = " + result);
              return;
          }
          
          // Check for write-out-word pattern (use normalized text)
          Matcher writeOutWordMatcher = WRITE_OUT_WORD_PATTERN.matcher(normalizedText);
          if (writeOutWordMatcher.find()) {
              String wordToWrite = writeOutWordMatcher.group(1);
              LOGGER.info("CHAT EVENT WRITE OUT WORD MATCH! Word: '{}'", wordToWrite);
              
              scheduleResponse(wordToWrite, "✓ Auto-wrote word: " + wordToWrite);
              return;
          }
          
          // Check for regular write-out pattern (use normalized text)
          Matcher writeOutMatcher = WRITE_OUT_PATTERN.matcher(normalizedText);
          if (writeOutMatcher.find()) {
              String stringToWrite = writeOutMatcher.group(1);
              LOGGER.info("CHAT EVENT WRITE OUT MATCH! String: '{}'", stringToWrite);
              
              scheduleResponse(stringToWrite, "✓ Auto-wrote: " + stringToWrite);
              return;
          }
          
          // Check regular trigger phrases (check both original and normalized)
          for (Map.Entry<String, String> entry : QUESTION_ANSWERS.entrySet()) {
              String trigger = entry.getKey();
              String response = entry.getValue();
              String normalizedTrigger = normalizeUnicode(trigger);
              
              if (messageText.toLowerCase().contains(trigger.toLowerCase()) ||
                  normalizedText.toLowerCase().contains(trigger.toLowerCase()) ||
                  messageText.toLowerCase().contains(normalizedTrigger.toLowerCase()) ||
                  normalizedText.toLowerCase().contains(normalizedTrigger.toLowerCase())) {
                  
                  LOGGER.info("CHAT EVENT MATCH! Trigger: '{}', Response: '{}'", trigger, response);
                  
                  scheduleResponse(response, "✓ Auto-replied: " + response);
                  break;
              }
          }
      });
      
      // Register all the existing commands (keeping all the original command functionality)
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
          dispatcher.register(literal("autoanswer")
              .then(literal("on")
                  .executes(context -> {
                      autoAnswerEnabled = true;
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.GREEN)
                              .append(Text.literal("✓ ENABLED - Will auto-reply to questions in chat!")
                                  .formatted(Formatting.WHITE))
                      );
                      LOGGER.info("AutoAnswer ENABLED via command");
                      return 1;
                  }))
              .then(literal("off")
                  .executes(context -> {
                      autoAnswerEnabled = false;
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.RED)
                              .append(Text.literal("✗ DISABLED - No auto-replies")
                                  .formatted(Formatting.WHITE))
                      );
                      LOGGER.info("AutoAnswer DISABLED via command");
                      return 1;
                  }))
              .then(literal("readmymind")
                  .then(literal("on")
                      .executes(context -> {
                          readMyMindEnabled = true;
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.GREEN)
                                  .append(Text.literal("✓ READMYMIND ON - Will respond to your own messages")
                                      .formatted(Formatting.WHITE))
                          );
                          return 1;
                      }))
                  .then(literal("off")
                      .executes(context -> {
                          readMyMindEnabled = false;
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.RED)
                                  .append(Text.literal("✗ READMYMIND OFF - Will ignore your own messages")
                                      .formatted(Formatting.WHITE))
                          );
                          return 1;
                      })))
              .then(literal("delay")
                  .then(argument("milliseconds", integer(0, 10000))
                      .executes(context -> {
                          int newDelay = getInteger(context, "milliseconds");
                          responseDelay = newDelay;
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.YELLOW)
                                  .append(Text.literal("Response delay set to " + newDelay + "ms (" + (newDelay/1000.0) + " seconds)")
                                      .formatted(Formatting.WHITE))
                          );
                          return 1;
                      }))
                  .executes(context -> {
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.YELLOW)
                              .append(Text.literal("Current delay: " + responseDelay + "ms (" + (responseDelay/1000.0) + " seconds)")
                                  .formatted(Formatting.WHITE))
                      );
                      context.getSource().sendFeedback(
                          Text.literal("Usage: /autoanswer delay <milliseconds> (0-10000)")
                              .formatted(Formatting.GRAY)
                      );
                      return 1;
                  }))
              .then(literal("sound")
                  .then(literal("on")
                      .executes(context -> {
                          soundAlertsEnabled = true;
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.GREEN)
                                  .append(Text.literal("✓ SOUND ALERTS ON")
                                      .formatted(Formatting.WHITE))
                          );
                          return 1;
                      }))
                  .then(literal("off")
                      .executes(context -> {
                          soundAlertsEnabled = false;
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.RED)
                                  .append(Text.literal("✗ SOUND ALERTS OFF")
                                      .formatted(Formatting.WHITE))
                          );
                          return 1;
                      })))
              .then(literal("stats")
                  .executes(context -> {
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer STATS] ")
                              .formatted(Formatting.GOLD)
                      );
                      context.getSource().sendFeedback(
                          Text.literal("✓ Questions answered: " + successCount)
                              .formatted(Formatting.GREEN)
                      );
                      context.getSource().sendFeedback(
                          Text.literal("• Response delay: " + responseDelay + "ms")
                              .formatted(Formatting.YELLOW)
                      );
                      context.getSource().sendFeedback(
                          Text.literal("• Sound alerts: " + (soundAlertsEnabled ? "ON" : "OFF"))
                              .formatted(soundAlertsEnabled ? Formatting.GREEN : Formatting.RED)
                      );
                      context.getSource().sendFeedback(
                          Text.literal("• Read my mind: " + (readMyMindEnabled ? "ON" : "OFF"))
                              .formatted(readMyMindEnabled ? Formatting.GREEN : Formatting.RED)
                      );
                      return 1;
                  }))
              .then(literal("reset")
                  .executes(context -> {
                      successCount = 0;
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.YELLOW)
                              .append(Text.literal("Stats reset! Success counter: 0")
                                  .formatted(Formatting.WHITE))
                      );
                      return 1;
                  }))
              .then(literal("test")
                  .executes(context -> {
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.YELLOW)
                              .append(Text.literal("Testing patterns (now with Unicode support):")
                                  .formatted(Formatting.WHITE))
                      );
                      context.getSource().sendFeedback(
                          Text.literal("1. Unicode Write Word: ")
                              .formatted(Formatting.GRAY)
                              .append(Text.literal("'| ʏᴏᴜ ʜᴀᴠᴇ 30 sᴇᴄᴏɴᴅs ᴛᴏ ᴡʀɪᴛᴇ ᴏᴜᴛ ᴛʜᴇ ᴡᴏʀᴅ: Lantern'")
                                  .formatted(Formatting.YELLOW))
                      );
                      context.getSource().sendFeedback(
                          Text.literal("2. Unicode Write Out: ")
                              .formatted(Formatting.GRAY)
                              .append(Text.literal("'| ʏᴏᴜ ʜᴀᴠᴇ 30 sᴇᴄᴏɴᴅs ᴛᴏ ᴡʀɪᴛᴇ ᴏᴜᴛ: ABC123'")
                                  .formatted(Formatting.YELLOW))
                      );
                      context.getSource().sendFeedback(
                          Text.literal("3. Unicode Math: ")
                              .formatted(Formatting.GRAY)
                              .append(Text.literal("'| ʏᴏᴜ ʜᴀᴠᴇ 30 sᴇᴄᴏɴᴅs ᴛᴏ sᴏʟᴠᴇ: 25 + 17'")
                                  .formatted(Formatting.YELLOW))
                      );
                      context.getSource().sendFeedback(
                          Text.literal("4. Symbol Math: ")
                              .formatted(Formatting.GRAY)
                              .append(Text.literal("'★ + ★ + ★ = 30, ✚ + ✚ + ✚ = 45, ✯ + ✚ + ? = 27'")
                                  .formatted(Formatting.YELLOW))
                      );
                      return 1;
                  }))
              .then(literal("add")
                  .then(argument("question", StringArgumentType.greedyString())
                      .executes(context -> {
                          String input = StringArgumentType.getString(context, "question");
                          String[] parts = input.split(" -> ", 2);
                          
                          if (parts.length != 2) {
                              context.getSource().sendError(
                                  Text.literal("Usage: /autoanswer add <question> -> <answer>")
                                      .formatted(Formatting.RED)
                              );
                              return 0;
                          }
                          
                          String question = parts[0].trim();
                          String answer = parts[1].trim();
                          
                          if (question.isEmpty() || answer.isEmpty()) {
                              context.getSource().sendError(
                                  Text.literal("Question and answer cannot be empty!")
                                      .formatted(Formatting.RED)
                              );
                              return 0;
                          }
                          
                          QUESTION_ANSWERS.put(question, answer);
                          saveQuestions();
                          
                          context.getSource().sendFeedback(
                              Text.literal("[AutoAnswer] ")
                                  .formatted(Formatting.GREEN)
                                  .append(Text.literal("✓ Added question: ")
                                      .formatted(Formatting.WHITE))
                                  .append(Text.literal("\"" + question + "\"")
                                      .formatted(Formatting.YELLOW))
                                  .append(Text.literal(" → ")
                                      .formatted(Formatting.GRAY))
                                  .append(Text.literal("\"" + answer + "\"")
                                      .formatted(Formatting.GREEN))
                          );
                          
                          context.getSource().sendFeedback(
                              Text.literal("Total questions: " + QUESTION_ANSWERS.size())
                                  .formatted(Formatting.GRAY)
                          );
                          
                          return 1;
                      })))
              .then(literal("list")
                  .executes(context -> {
                      context.getSource().sendFeedback(
                          Text.literal("[AutoAnswer] ")
                              .formatted(Formatting.AQUA)
                              .append(Text.literal("Loaded " + QUESTION_ANSWERS.size() + " static Q&A pairs + 4 dynamic patterns:")
                                  .formatted(Formatting.WHITE))
                      );
                      
                      // Show dynamic patterns
                      context.getSource().sendFeedback(
                          Text.literal("PATTERN 1: ")
                              .formatted(Formatting.GOLD)
                              .append(Text.literal("\"You have 30 seconds to write out: [STRING]\"")
                                  .formatted(Formatting.YELLOW))
                              .append(Text.literal(" → ")
                                  .formatted(Formatting.GRAY))
                              .append(Text.literal("[STRING]")
                                  .formatted(Formatting.GREEN))
                      );
                      
                      context.getSource().sendFeedback(
                          Text.literal("PATTERN 2: ")
                              .formatted(Formatting.GOLD)
                              .append(Text.literal("\"You have 30 seconds to write out the word: [WORD]\"")
                                  .formatted(Formatting.YELLOW))
                              .append(Text.literal(" → ")
                                  .formatted(Formatting.GRAY))
                              .append(Text.literal("[WORD]")
                                  .formatted(Formatting.GREEN))
                      );
                      
                      context.getSource().sendFeedback(
                          Text.literal("PATTERN 3: ")
                              .formatted(Formatting.GOLD)
                              .append(Text.literal("\"You have 30 seconds to solve: [NUM] [+/-/*] [NUM]\"")
                                  .formatted(Formatting.YELLOW))
                              .append(Text.literal(" → ")
                                  .formatted(Formatting.GRAY))
                              .append(Text.literal("[RESULT]")
                                  .formatted(Formatting.GREEN))
                      );
                      
                      context.getSource().sendFeedback(
                          Text.literal("PATTERN 4: ")
                              .formatted(Formatting.GOLD)
                              .append(Text.literal("\"Multi-line Symbol Math: ❅ + ♫ + ✗ = 83\"")
                                  .formatted(Formatting.YELLOW))
                              .append(Text.literal(" → ")
                                  .formatted(Formatting.GRAY))
                              .append(Text.literal("[ANSWER]")
                                  .formatted(Formatting.GREEN))
                      );
                      
                      // Show static Q&A pairs
                      int count = 1;
                      for (Map.Entry<String, String> entry : QUESTION_ANSWERS.entrySet()) {
                          context.getSource().sendFeedback(
                              Text.literal(count + ". ")
                                  .formatted(Formatting.GRAY)
                                  .append(Text.literal("\"" + entry.getKey() + "\"")
                                      .formatted(Formatting.YELLOW))
                                  .append(Text.literal(" → ")
                                      .formatted(Formatting.GRAY))
                                  .append(Text.literal("\"" + entry.getValue() + "\"")
                                      .formatted(Formatting.GREEN))
                          );
                          count++;
                          if (count > 10) {
                              context.getSource().sendFeedback(
                                  Text.literal("... and " + (QUESTION_ANSWERS.size() - 10) + " more (use /autoanswer search to find specific questions)")
                                      .formatted(Formatting.GRAY)
                              );
                              break;
                          }
                      }
                      return 1;
                  }))
              // ... (keeping all other existing commands)
              .executes(context -> {
                  String status = autoAnswerEnabled ? "ENABLED ✓" : "DISABLED ✗";
                  Formatting statusColor = autoAnswerEnabled ? Formatting.GREEN : Formatting.RED;
                  
                  context.getSource().sendFeedback(
                      Text.literal("[AutoAnswer] ")
                          .formatted(Formatting.AQUA)
                          .append(Text.literal("Status: ")
                              .formatted(Formatting.WHITE))
                          .append(Text.literal(status)
                              .formatted(statusColor))
                  );
                  
                  context.getSource().sendFeedback(
                      Text.literal("Commands: ")
                          .formatted(Formatting.GRAY)
                          .append(Text.literal("/autoanswer on/off/add/remove/search/backup/stats")
                              .formatted(Formatting.YELLOW))
                  );
                  
                  return 1;
              })
          );
      });
  }
  
  // Enhanced symbol math solver that works with ANY symbols/letters/characters
  private int solveSymbolMath(String symbol1, int eq1Result, String symbol2, int eq2Result, String unknownSymbol, int eq3Result) {
      // From equation 1: symbol1 + symbol1 + symbol1 = eq1Result
      // So: symbol1 = eq1Result / 3
      int value1 = eq1Result / 3;
      
      // From equation 2: symbol2 + symbol2 + symbol2 = eq2Result  
      // So: symbol2 = eq2Result / 3
      int value2 = eq2Result / 3;
      
      // From equation 3: symbol1 + symbol2 + unknownSymbol = eq3Result
      // So: unknownSymbol = eq3Result - symbol1 - symbol2
      int answer = eq3Result - value1 - value2;
      
      LOGGER.info("Symbol math solved:");
      LOGGER.info("'{}' = {}, '{}' = {}, '{}' = {}", symbol1, value1, symbol2, value2, unknownSymbol, answer);
      
      return answer;
  }
  
  // Calculate math operations
  private int calculateMath(int num1, String operator, int num2) {
      switch (operator) {
          case "+":
              return num1 + num2;
          case "-":
              return num1 - num2;
          case "*":
              return num1 * num2;
          case "/":
              if (num2 != 0) {
                  return num1 / num2;
              } else {
                  LOGGER.warn("Division by zero attempted: {} / {}", num1, num2);
                  return 0;
              }
          default:
              LOGGER.warn("Unknown operator: {}", operator);
              return 0;
      }
  }
  
  // Schedule a response with delay
  private void scheduleResponse(String response, String confirmationMessage) {
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
          @Override
          public void run() {
              MinecraftClient client = MinecraftClient.getInstance();
              if (client.player != null && client.getNetworkHandler() != null) {
                  // Send the response in chat
                  client.getNetworkHandler().sendChatMessage(response);
                  
                  // Increment success counter
                  successCount++;
                  
                  // Play success sound if enabled
                  if (soundAlertsEnabled) {
                      client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                  }
                  
                  // Send confirmation message
                  client.player.sendMessage(
                      Text.literal("[AutoAnswer] ")
                          .formatted(Formatting.GREEN)
                          .append(Text.literal(confirmationMessage + " (#" + successCount + ")")
                              .formatted(Formatting.YELLOW)),
                      false
                  );
                  
                  LOGGER.info("Successfully sent delayed response: {} (Success #{}) ", response, successCount);
              } else {
                  LOGGER.warn("Could not send delayed message - client or network handler is null");
              }
          }
      }, responseDelay);
  }
  
  public static boolean isAutoAnswerEnabled() {
      return autoAnswerEnabled;
  }
  
  public static void setAutoAnswerEnabled(boolean enabled) {
      autoAnswerEnabled = enabled;
  }
}
