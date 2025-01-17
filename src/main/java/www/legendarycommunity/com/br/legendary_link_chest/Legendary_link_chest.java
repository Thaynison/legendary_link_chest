package www.legendarycommunity.com.br.legendary_link_chest;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class Legendary_link_chest extends JavaPlugin implements Listener {

    private Economy economy;
    private File dataFolder;
    private final HashMap<UUID, Location> firstChestSelection = new HashMap<>();

    @Override
    public void onEnable() {
        // Registra o Listener para eventos
        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        dataFolder = new File(getDataFolder(), "uuid");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        setupEconomy();
        getServer().getScheduler().runTaskTimer(this, this::transferItems, 20L, 20L); // A cada 1 segundo

        // Carrega o prefixo do arquivo de configuração
        String prefix = getConfig().getString("prefix", "&f[&bLegendary&3Community&f] ");
        getConfig().set("prefix", prefix);
        saveConfig();
    }


    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault não está instalado!");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private String formatMessage(String message) {
        String prefix = getConfig().getString("prefix", "&f[&bLegendary&3Community&f] ");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(formatMessage("Apenas jogadores podem usar este comando."));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("linkarbau")) {
            if (args.length < 2) {
                player.sendMessage(formatMessage("Uso correto: /linkarbau <bau1|bau2> <nome_do_link>"));
                return true;
            }

            String bau = args[0].toLowerCase();
            String nomeLink = args[1];

            // Verifica se bau é válido
            if (!bau.equals("bau1") && !bau.equals("bau2")) {
                player.sendMessage(formatMessage("Opções válidas para <bau1|bau2> são: 'bau1' ou 'bau2'."));
                return true;
            }

            Block targetBlock = player.getTargetBlockExact(5);
            if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
                player.sendMessage(formatMessage("Você deve mirar em um baú."));
                return true;
            }

            Location chestLocation = targetBlock.getLocation();
            UUID playerUUID = player.getUniqueId();

            if (!firstChestSelection.containsKey(playerUUID)) {
                if ("bau1".equals(bau)) {
                    firstChestSelection.put(playerUUID, chestLocation);
                    player.sendMessage(formatMessage("Primeiro baú (entrada) selecionado para o link: " + nomeLink));
                } else {
                    player.sendMessage(formatMessage("Você deve primeiro selecionar o primeiro baú com /linkarbau bau1 <nome_do_link>"));
                }
            } else {
                Location firstChestLocation = firstChestSelection.get(playerUUID);
                firstChestSelection.remove(playerUUID);

                double valorBau = getConfig().getDouble("valor_bau");
                if (economy.getBalance(player) < valorBau) {
                    player.sendMessage(formatMessage("Você não tem dinheiro suficiente para vincular os baús. O valor necessário é: " + valorBau));
                    return true;
                }

                economy.withdrawPlayer(player, valorBau);

                // Salvar os baús e o nome do link no arquivo do jogador
                String playerFileName = playerUUID.toString() + ".yml";
                File playerFile = new File(dataFolder, playerFileName);
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                String linkId = nomeLink;
                playerConfig.set(linkId + ".entrada", serializeLocation(firstChestLocation));
                playerConfig.set(linkId + ".saida", serializeLocation(chestLocation));
                playerConfig.set(linkId + ".player", player.getName()); // Salvar o nick do jogador

                try {
                    playerConfig.save(playerFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                player.sendMessage(formatMessage("Os baús foram vinculados com sucesso ao link '" + nomeLink + "'! Você foi cobrado " + valorBau + " moedas."));
            }
            return true;
        }

        else if (command.getName().equalsIgnoreCase("linkarfiltro")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("listar")) {
                StringBuilder itens = new StringBuilder(formatMessage("Lista de itens do Minecraft:\n"));
                for (Material material : Material.values()) {
                    if (material.isItem()) {
                        itens.append(material.name()).append(", ");
                    }
                }
                player.sendMessage(itens.substring(0, itens.length() - 2)); // Remove vírgula extra
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(formatMessage("Uso correto: /linkarfiltro <nome_do_link> <item_do_minecraft>"));
                return true;
            }

            String nomeLink = args[0];
            String itemFiltro = args[1].toUpperCase();

            // Verifica se o item é válido
            Material material = Material.matchMaterial(itemFiltro);
            if (material == null) {
                player.sendMessage(formatMessage("O item '" + itemFiltro + "' não é válido."));
                return true;
            }

            UUID playerUUID = player.getUniqueId();
            String playerFileName = playerUUID.toString() + ".yml";
            File playerFile = new File(dataFolder, playerFileName);
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            if (!playerConfig.contains(nomeLink)) {
                player.sendMessage(formatMessage("O link '" + nomeLink + "' não existe."));
                return true;
            }

            // Recupera a lista de filtros existente ou cria uma nova
            List<String> filtros = playerConfig.getStringList(nomeLink + ".filtros");
            if (!filtros.contains(itemFiltro)) {
                filtros.add(itemFiltro);
                playerConfig.set(nomeLink + ".filtros", filtros);

                try {
                    playerConfig.save(playerFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                player.sendMessage(formatMessage("Filtro de item '" + itemFiltro + "' adicionado ao link '" + nomeLink + "'."));
            } else {
                player.sendMessage(formatMessage("O item '" + itemFiltro + "' já está na lista de filtros do link '" + nomeLink + "'."));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("removerfiltros")) {
            if (args.length < 1) {
                player.sendMessage(formatMessage("Uso correto: /removerfiltros <nome_do_link>"));
                return true;
            }

            String nomeLink = args[0];
            UUID playerUUID = player.getUniqueId();
            String playerFileName = playerUUID.toString() + ".yml";
            File playerFile = new File(dataFolder, playerFileName);
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            if (!playerConfig.contains(nomeLink)) {
                player.sendMessage(formatMessage("O link '" + nomeLink + "' não existe."));
                return true;
            }

            // Remove todos os filtros
            playerConfig.set(nomeLink + ".filtros", null);
            try {
                playerConfig.save(playerFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            player.sendMessage(formatMessage("Todos os filtros foram removidos do link '" + nomeLink + "'."));
            return true;
        }

        return false;
    }

    private void transferItems() {
        for (File playerFile : dataFolder.listFiles()) {
            if (playerFile.isFile() && playerFile.getName().endsWith(".yml")) {
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                for (String linkId : playerConfig.getKeys(false)) {
                    Location entradaLoc = deserializeLocation(playerConfig.getString(linkId + ".entrada"));
                    Location saidaLoc = deserializeLocation(playerConfig.getString(linkId + ".saida"));
                    List<String> filtrosItens = playerConfig.getStringList(linkId + ".filtros");

                    if (entradaLoc == null || saidaLoc == null) continue;

                    Block entradaBlock = entradaLoc.getBlock();
                    Block saidaBlock = saidaLoc.getBlock();

                    if (entradaBlock.getType() == Material.CHEST && saidaBlock.getType() == Material.CHEST) {
                        Chest entradaChest = (Chest) entradaBlock.getState();
                        Chest saidaChest = (Chest) saidaBlock.getState();

                        for (int i = 0; i < entradaChest.getInventory().getSize(); i++) {
                            ItemStack itemStack = entradaChest.getInventory().getItem(i);
                            if (itemStack != null) {
                                // Verifica o filtro
                                if (filtrosItens.isEmpty() || filtrosItens.contains(itemStack.getType().toString())) {
                                    // Testa se há espaço no baú de destino
                                    HashMap<Integer, ItemStack> itemsLeft = saidaChest.getInventory().addItem(itemStack);
                                    if (itemsLeft.isEmpty()) {
                                        // Remove o item do baú de entrada apenas se ele foi transferido com sucesso
                                        entradaChest.getInventory().clear(i);
                                    } else {
                                        // Baú de destino está cheio, interrompe a transferência deste item
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ();
    }

    private Location deserializeLocation(String locationString) {
        if (locationString == null || locationString.isEmpty()) return null;

        String[] parts = locationString.split(",");
        if (parts.length != 4) return null;

        try {
            return new Location(
                    Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Evento que detecta a quebra do baú
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Verifique se o bloco quebrado é um baú
        if (block.getType() == Material.CHEST) {
            for (File playerFile : dataFolder.listFiles()) {
                if (playerFile.isFile() && playerFile.getName().endsWith(".yml")) {
                    YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                    for (String linkId : playerConfig.getKeys(false)) {
                        Location entradaLoc = deserializeLocation(playerConfig.getString(linkId + ".entrada"));
                        Location saidaLoc = deserializeLocation(playerConfig.getString(linkId + ".saida"));

                        // Verifica se o baú quebrado é o baú de entrada ou saída
                        if (entradaLoc != null && entradaLoc.equals(block.getLocation())) {
                            // Remove o link do UUID do jogador
                            playerConfig.set(linkId, null);
                            savePlayerConfig(playerConfig, playerFile);
                            player.sendMessage(formatMessage("Link de baú de entrada removido com sucesso!"));
                        } else if (saidaLoc != null && saidaLoc.equals(block.getLocation())) {
                            // Remove o link do UUID do jogador
                            playerConfig.set(linkId, null);
                            savePlayerConfig(playerConfig, playerFile);
                            player.sendMessage(formatMessage("Link de baú de saída removido com sucesso!"));
                        }
                    }
                }
            }
        }
    }

    private void savePlayerConfig(YamlConfiguration playerConfig, File playerFile) {
        try {
            playerConfig.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
