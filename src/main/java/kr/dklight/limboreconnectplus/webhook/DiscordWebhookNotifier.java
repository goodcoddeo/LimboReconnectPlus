/*
 * Copyright (C) 2022 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package kr.dklight.limboreconnectplus.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import kr.dklight.limboreconnectplus.Config;
import kr.dklight.limboreconnectplus.LimboReconnect;

public class DiscordWebhookNotifier {

  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private DiscordWebhookNotifier() {
  }

  public static void notifyServerDown(String serverName, int queueSize) {
    Config.DiscordWebhook webhookConfig = LimboReconnect.CONFIG.discordWebhook;
    if (webhookConfig == null || !webhookConfig.enabled || webhookConfig.url == null || webhookConfig.url.isEmpty()) {
      return;
    }
    String message = webhookConfig.serverDownMessage
        .replace("%server%", serverName)
        .replace("%queue%", String.valueOf(queueSize));
    DiscordWebhookNotifier.send(webhookConfig, message);
  }

  public static void notifyServerUp(String serverName, int queueSize) {
    Config.DiscordWebhook webhookConfig = LimboReconnect.CONFIG.discordWebhook;
    if (webhookConfig == null || !webhookConfig.enabled || webhookConfig.url == null || webhookConfig.url.isEmpty()) {
      return;
    }
    String message = webhookConfig.serverUpMessage
        .replace("%server%", serverName)
        .replace("%queue%", String.valueOf(queueSize));
    DiscordWebhookNotifier.send(webhookConfig, message);
  }

  private static void send(Config.DiscordWebhook config, String message) {
    try {
      String json = DiscordWebhookNotifier.buildJsonPayload(config, message);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(config.url))
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json; charset=UTF-8")
          .header("User-Agent", "LimboReconnectPlus")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
          .whenComplete((response, throwable) -> {
            if (throwable != null && LimboReconnect.CONFIG.debug) {
              LimboReconnect.getLogger().warn("Failed to send Discord webhook: {}", throwable.getMessage());
            }
          });
    } catch (Exception e) {
      if (LimboReconnect.CONFIG.debug) {
        LimboReconnect.getLogger().warn("Error preparing Discord webhook: {}", e.getMessage());
      }
    }
  }

  private static String buildJsonPayload(Config.DiscordWebhook config, String message) {
    StringBuilder builder = new StringBuilder();
    builder.append("{");
    builder.append("\"content\":\"").append(DiscordWebhookNotifier.escapeJson(message)).append("\"");
    if (config.username != null && !config.username.isEmpty()) {
      builder.append(",\"username\":\"").append(DiscordWebhookNotifier.escapeJson(config.username)).append("\"");
    }
    if (config.avatarUrl != null && !config.avatarUrl.isEmpty()) {
      builder.append(",\"avatar_url\":\"").append(DiscordWebhookNotifier.escapeJson(config.avatarUrl)).append("\"");
    }
    builder.append("}");
    return builder.toString();
  }

  private static String escapeJson(String input) {
    if (input == null) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(input.length() + 16);
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (c < 0x20) {
            escaped.append(String.format("\\u%04x", (int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }
}
