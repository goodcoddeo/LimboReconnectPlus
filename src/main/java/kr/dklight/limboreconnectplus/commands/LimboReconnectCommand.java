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

package kr.dklight.limboreconnectplus.commands;

import static kr.dklight.limboreconnectplus.LimboReconnect.CONFIG;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import kr.dklight.limboreconnectplus.LimboReconnect;
import kr.dklight.limboreconnectplus.handler.ReconnectHandler;
import kr.dklight.limboreconnectplus.queue.ServerQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LimboReconnectCommand implements SimpleCommand {

  private static final List<Component> HELP_MESSAGE = List.of(
      Component.text("LimboReconnectPlus by dklight - Powered by LimboAPI", NamedTextColor.GOLD),
      Component.empty()
  );

  private static final Component AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("Available subcommands:", NamedTextColor.WHITE);
  private static final Component NO_AVAILABLE_SUBCOMMANDS_MESSAGE = Component.text("No available subcommands.", NamedTextColor.WHITE);

  private final LimboReconnect plugin;

  public LimboReconnectCommand(LimboReconnect plugin) {
    this.plugin = plugin;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      String argument = args[0];
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
          .collect(Collectors.toList());
    } else if (args.length == 2) {
      String sub = args[0].toLowerCase(Locale.ROOT);
      String argument = args[1];
      if (sub.equals("pause") || sub.equals("resume") || sub.equals("kickall") || sub.equals("status") || sub.equals("stats")) {
        return this.plugin.getServer().getAllServers().stream()
            .map(s -> s.getServerInfo().getName())
            .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
            .collect(Collectors.toList());
      } else if (sub.equals("skip")) {
        List<String> queuedPlayers = new ArrayList<>();
        for (ServerQueue q : this.plugin.getQueueManager().getQueues().values()) {
          for (ReconnectHandler h : q.getQueue()) {
            if (h.getPlayer() != null) {
              queuedPlayers.add(h.getPlayer().getProxyPlayer().getUsername());
            }
          }
        }
        return queuedPlayers.stream()
            .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
            .collect(Collectors.toList());
      }
      return ImmutableList.of();
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    int argsAmount = args.length;
    if (argsAmount > 0) {
      try {
        Subcommand subcommand = Subcommand.valueOf(args[0].toUpperCase(Locale.ROOT));
        if (!subcommand.hasPermission(source)) {
          this.showHelp(source);
          return;
        }

        subcommand.executor.execute(this, source, args);
      } catch (IllegalArgumentException e) {
        this.showHelp(source);
      }
    } else {
      this.showHelp(source);
    }
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return invocation.source().getPermissionValue("limboreconnectplus.command.help") != Tristate.FALSE;
  }

  private void showHelp(CommandSource source) {
    HELP_MESSAGE.forEach(source::sendMessage);

    List<Subcommand> availableSubcommands = Arrays.stream(Subcommand.values())
        .filter(command -> command.hasPermission(source))
        .toList();

    if (!availableSubcommands.isEmpty()) {
      source.sendMessage(AVAILABLE_SUBCOMMANDS_MESSAGE);
      availableSubcommands.forEach(command -> source.sendMessage(command.getMessageLine()));
    } else {
      source.sendMessage(NO_AVAILABLE_SUBCOMMANDS_MESSAGE);
    }
  }

  private enum Subcommand {
    RELOAD("Reload config.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      parent.plugin.reload();
      source.sendMessage(LimboReconnect.getSerializer().deserialize(CONFIG.messages.reload));
    }),

    PAUSE("Pause queue for a server.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      if (args.length < 2) {
        source.sendMessage(Component.text("Usage: /limboreconnectplus pause <server>", NamedTextColor.RED));
        return;
      }
      String serverName = args[1];
      ServerQueue queue = parent.plugin.getQueueManager().getQueueByName(serverName);
      if (queue == null) {
        source.sendMessage(Component.text("No active queue found for: " + serverName, NamedTextColor.RED));
        return;
      }
      queue.setPaused(true);
      source.sendMessage(Component.text("Queue paused for server " + serverName, NamedTextColor.GREEN));
    }),

    RESUME("Resume queue for a server.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      if (args.length < 2) {
        source.sendMessage(Component.text("Usage: /limboreconnectplus resume <server>", NamedTextColor.RED));
        return;
      }
      String serverName = args[1];
      ServerQueue queue = parent.plugin.getQueueManager().getQueueByName(serverName);
      if (queue == null) {
        source.sendMessage(Component.text("No active queue found for: " + serverName, NamedTextColor.RED));
        return;
      }
      queue.setPaused(false);
      source.sendMessage(Component.text("Queue resumed for server " + serverName, NamedTextColor.GREEN));
    }),

    KICKALL("Kick or redirect all waiting players.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      if (args.length < 2) {
        source.sendMessage(Component.text("Usage: /limboreconnectplus kickall <server> [fallbackServer]", NamedTextColor.RED));
        return;
      }
      String serverName = args[1];
      ServerQueue queue = parent.plugin.getQueueManager().getQueueByName(serverName);
      if (queue == null) {
        source.sendMessage(Component.text("No active queue found for: " + serverName, NamedTextColor.RED));
        return;
      }
      String fallback = args.length >= 3 ? args[2] : CONFIG.fallbackServer;
      int kicked = queue.kickAll(fallback, Component.text("Disconnected by administrator.", NamedTextColor.RED));
      source.sendMessage(Component.text("Kicked or redirected " + kicked + " players from " + serverName + " queue.", NamedTextColor.GREEN));
    }),

    SKIP("Move a player to front of queue.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      if (args.length < 2) {
        source.sendMessage(Component.text("Usage: /limboreconnectplus skip <player>", NamedTextColor.RED));
        return;
      }
      String playerName = args[1];
      boolean found = false;
      for (ServerQueue q : parent.plugin.getQueueManager().getQueues().values()) {
        if (q.skip(playerName)) {
          found = true;
          source.sendMessage(Component.text("Moved " + playerName + " to the front of the queue.", NamedTextColor.GREEN));
          break;
        }
      }
      if (!found) {
        source.sendMessage(Component.text("Player not found in any queue: " + playerName, NamedTextColor.RED));
      }
    }),

    STATUS("View queue status.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      source.sendMessage(Component.text("--- LimboReconnectPlus Queue Status ---", NamedTextColor.GOLD));
      if (parent.plugin.getQueueManager().getQueues().isEmpty()) {
        source.sendMessage(Component.text("No active server queues.", NamedTextColor.GRAY));
        return;
      }
      parent.plugin.getQueueManager().getQueues().forEach((regServer, queue) -> {
        String name = regServer.getServerInfo().getName();
        String pausedInfo = queue.isPaused() ? " [PAUSED]" : "";
        source.sendMessage(Component.text("Server " + name + pausedInfo + ": "
            + queue.getTotal() + " players in queue | Status: " + queue.getServerStatus(), NamedTextColor.YELLOW));
      });
    }),

    STATS("View queue statistics.", (LimboReconnectCommand parent, CommandSource source, String[] args) -> {
      source.sendMessage(Component.text("--- LimboReconnectPlus Queue Statistics ---", NamedTextColor.GOLD));
      if (args.length >= 2) {
        String serverName = args[1];
        ServerQueue queue = parent.plugin.getQueueManager().getQueueByName(serverName);
        if (queue == null) {
          source.sendMessage(Component.text("No queue found for server: " + serverName, NamedTextColor.RED));
          return;
        }
        source.sendMessage(Component.text("Server: ", NamedTextColor.GRAY)
            .append(Component.text(serverName, NamedTextColor.YELLOW))
            .append(Component.text(" (" + queue.getServerStatus() + ")", NamedTextColor.GREEN)));
        source.sendMessage(Component.text("  Current Queue: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(queue.getTotal()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Peak Queue: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(queue.getStats().getPeakQueueSize()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Total Enqueued: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(queue.getStats().getTotalEnqueued()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Total Dispatched: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(queue.getStats().getTotalDispatched()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Total Left: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(queue.getStats().getTotalLeft()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Avg Wait Time: ", NamedTextColor.GRAY)
            .append(Component.text(String.format(Locale.ROOT, "%.1fs",
                queue.getStats().getAverageWaitTimeSeconds()), NamedTextColor.GOLD)));
        source.sendMessage(Component.text("  Batch Settings: ", NamedTextColor.GRAY)
            .append(Component.text("size=" + queue.resolveBatchSize() + ", interval="
                + queue.resolveBatchInterval() + "ms", NamedTextColor.AQUA)));
      } else {
        if (parent.plugin.getQueueManager().getQueues().isEmpty()) {
          source.sendMessage(Component.text("No active server queues.", NamedTextColor.GRAY));
          return;
        }
        parent.plugin.getQueueManager().getQueues().forEach((regServer, queue) -> {
          String name = regServer.getServerInfo().getName();
          source.sendMessage(Component.text("Server " + name + ": ", NamedTextColor.YELLOW)
              .append(Component.text("Queue=" + queue.getTotal() + " (Peak="
                  + queue.getStats().getPeakQueueSize() + ") | ", NamedTextColor.GRAY))
              .append(Component.text("Dispatched=" + queue.getStats().getTotalDispatched() + " | ", NamedTextColor.GRAY))
              .append(Component.text("AvgWait=" + String.format(Locale.ROOT, "%.1fs",
                  queue.getStats().getAverageWaitTimeSeconds()), NamedTextColor.GRAY)));
        });
      }
    });

    private final String command;
    private final String description;
    private final SubcommandExecutor executor;

    Subcommand(String description, SubcommandExecutor executor) {
      this.command = this.name().toLowerCase(Locale.ROOT);
      this.description = description;
      this.executor = executor;
    }

    public boolean hasPermission(CommandSource source) {
      return source.hasPermission("limboreconnectplus.command." + this.command);
    }

    public Component getMessageLine() {
      return Component.textOfChildren(Component.text("  /limboreconnectplus " + this.command, NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY), Component.text(this.description, NamedTextColor.YELLOW)
      );
    }

    public String getCommand() {
      return this.command;
    }
  }

  private interface SubcommandExecutor {

    void execute(LimboReconnectCommand parent, CommandSource source, String[] args);
  }
}
