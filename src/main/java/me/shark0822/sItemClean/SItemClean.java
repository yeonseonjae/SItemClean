package me.shark0822.sItemClean;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SItemClean extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");

    private String prefix;
    private String itemCleanedMessage;
    private String clearCountdownMessage;
    private String alarmMode;
    private String reloadMessage;
    private String usageMessage;
    private boolean protectPlayerDroppedItems;
    private int cleanInterval;
    private int warningTime;

    private BukkitRunnable cleanerTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        // 명령어 등록
        getCommand("itemclean").setExecutor(this);
        getCommand("itemclean").setTabCompleter(this);

        startItemCleanerTask();
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        prefix = colorize(translateHexColorCodes(config.getString("prefix", "&7[ &9아이템 청소 &7]") + " ")); // 기본값 설정
        itemCleanedMessage = colorize(translateHexColorCodes(prefix + config.getString("item_cleaned")));
        clearCountdownMessage = colorize(translateHexColorCodes(prefix + config.getString("clear_countdown")));

        // 기본값을 actionbar로 설정하고, 설정 파일에서 가져온 값을 사용
        alarmMode = config.getString("alarm_message_type", "actionbar");

        // 알람 메시지 유형이 chat 또는 actionbar가 아니면 경고 메시지 출력
        if (!"chat".equalsIgnoreCase(alarmMode) && !"actionbar".equalsIgnoreCase(alarmMode)) {
            Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "알 수 없는 알람 메세지 유형 : '" + alarmMode + "'");
            config.set("alarm_message_type", "actionbar"); // 기본값으로 actionbar 설정
            saveConfig();
            alarmMode = "actionbar";
            Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.WHITE + "알림 메시지 유형이 잘못되어 기본값 'actionbar'(으)로 설정되었습니다.");
        }

        reloadMessage = colorize(translateHexColorCodes(prefix + config.getString("reload")));
        usageMessage = colorize(translateHexColorCodes(prefix + config.getString("usage")));
        protectPlayerDroppedItems = config.getBoolean("protect_player_dropped_items", true);
        cleanInterval = config.getInt("time") * 60; // 분 -> 초
        warningTime = Math.min(Math.max(config.getInt("count"), 5), 10);

        // count 값 범위 체크 및 조정
        int originalCount = config.getInt("count", 5);
        warningTime = Math.min(Math.max(originalCount, 3), 10);

        // 원래 값이 범위를 벗어났다면 메시지 출력
        if (originalCount != warningTime) {
            if (originalCount < 3) {
                Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "경고 시간이 최소값(3초)보다 작아 기본값(5초)으로 재설정되었습니다.");
            } else if (originalCount > 10) {
                Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "경고 시간이 최대값(10초)보다 커서 기본값(5초)으로 재설정되었습니다.");
            }

            // config 파일에 조정된 값 저장
            config.set("count", 5);
            saveConfig();
            warningTime = 5;
        }
    }

    private void startItemCleanerTask() {
        cleanerTask = new BukkitRunnable() {
            private int countdown = cleanInterval;

            @Override
            public void run() {
                if (countdown > 0 && countdown <= warningTime) {
                    broadcastMessage(clearCountdownMessage.replace("%time%", String.valueOf(countdown)));
                    Bukkit.getConsoleSender().sendMessage(clearCountdownMessage.replace("%time%", String.valueOf(countdown)));
                }

                if (countdown <= 0) {
                    int cleanedItems = cleanItems();
                    broadcastMessage(itemCleanedMessage.replace("%count%", String.valueOf(cleanedItems)));
                    Bukkit.getConsoleSender().sendMessage(itemCleanedMessage.replace("%count%", String.valueOf(cleanedItems)));
                    countdown = cleanInterval;
                } else {
                    countdown--;
                }
            }
        };
        cleanerTask.runTaskTimer(this, 0L, 20L);
    }

    private int cleanItems() {
        int totalCleaned = 0;
        for (World world : Bukkit.getWorlds()) {
            List<Entity> entities = world.getEntities();
            for (Entity entity : entities) {
                if (entity instanceof Item item) {
                    if (protectPlayerDroppedItems) {
                        if (item.getPickupDelay() <= 0 && item.getOwner() == null) {
                            item.remove();
                            totalCleaned++;
                        }
                    } else {
                        item.remove();
                        totalCleaned++;
                    }
                }
            }
        }
        return totalCleaned;
    }

    private void broadcastMessage(String message) {
        final String finalMessage = colorize(translateHexColorCodes(message));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if ("actionbar".equalsIgnoreCase(alarmMode)) {
                sendActionBar(player, finalMessage);
            } else if ("chat".equalsIgnoreCase(alarmMode)) {
                player.sendMessage(finalMessage);
            }
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private String colorize(final String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    private String translateHexColorCodes(final String message) {
        final char colorChar = org.bukkit.ChatColor.COLOR_CHAR;

        final Matcher matcher = HEX_PATTERN.matcher(message);
        final StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

        while (matcher.find()) {
            final String group = matcher.group(1);

            matcher.appendReplacement(buffer, colorChar + "x"
                    + colorChar + group.charAt(0) + colorChar + group.charAt(1)
                    + colorChar + group.charAt(2) + colorChar + group.charAt(3)
                    + colorChar + group.charAt(4) + colorChar + group.charAt(5));
        }

        return matcher.appendTail(buffer).toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("itemclean")) {
            if (args.length == 0) {
                sender.sendMessage(colorize(translateHexColorCodes(usageMessage)));
                return true;
            }

            if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("rl")) {
                reloadConfig();
                loadConfig();
                sender.sendMessage(colorize(translateHexColorCodes(reloadMessage)));

                if (cleanerTask != null) {
                    cleanerTask.cancel();
                    startItemCleanerTask();
                }
            } else if (args[0].equalsIgnoreCase("clean") || args[0].equalsIgnoreCase("cl")) {
                int cleanedItems = cleanItems();
                sender.sendMessage(colorize(translateHexColorCodes(itemCleanedMessage.replace("%count%", String.valueOf(cleanedItems)))));
            } else {
                sender.sendMessage(colorize(translateHexColorCodes(usageMessage)));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("itemclean")) {
            if (args.length == 1) {
                return Arrays.asList("reload", "rl", "clean", "cl");
            }
        }
        return null;
    }

    @Override
    public void onDisable() {
        if (cleanerTask != null) {
            cleanerTask.cancel();
        }
    }
}