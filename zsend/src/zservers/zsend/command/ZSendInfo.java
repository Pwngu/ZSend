package zservers.zsend.command;

import net.craftminecraft.bungee.bungeeyaml.bukkitapi.file.FileConfiguration;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.plugin.Command;
import zservers.zsend.main.ZSend;
import zservers.zsend.network.*;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ZSendInfo extends Command {

	private static ZSend main = ZSend.getInstance();

	public ZSendInfo() {

		super("zs");
	}

	@Override
	public void execute(CommandSender sender, String[] args) {

		if(!sender.hasPermission(ZSend.getInstance().getConfig().getString(ZSend.ADMIN_PERM_PATH))) {

			sender.sendMessage(new ComponentBuilder("You don't have permission to perform this command!").color(ChatColor.RED).create());
			return;
		}

		BaseComponent[] baseLine = new ComponentBuilder("--").bold(true).color(ChatColor.GOLD).append("--").color(ChatColor.BLUE)
				.append("--").color(ChatColor.GOLD).append("--").color(ChatColor.BLUE).append("--")
				.color(ChatColor.GOLD).append("--").color(ChatColor.BLUE).append("--").color(ChatColor.GOLD).create();

		FileConfiguration config = ZSend.getInstance().getConfig();

		Server server = main.getServer();

		if(args.length == 0) { //general Info

			synchronized (sender) {

				sender.sendMessage(baseLine);

				sender.sendMessage(new ComponentBuilder(main.getServerName()).color(ChatColor.YELLOW).bold(true).create());

                if(server.isHosting()) sender.sendMessage(new ComponentBuilder("HOSTING").color(ChatColor.GREEN).create());
                else sender.sendMessage(new ComponentBuilder("NOT HOSTING").color(ChatColor.RED).create());

                sender.sendMessage(baseLine);

				sender.sendMessage(new ComponentBuilder("Threads:\t").color(ChatColor.RED).append("" + main.getRunningThreadCount())
						.color(ChatColor.RED).append("/").append("" + config.getInt(ZSend.MAX_THREADS_PATH)).create());

				sender.sendMessage(new ComponentBuilder("Server: \t").color(ChatColor.YELLOW)
						.append("(" + server.getServerHandlerThreads().size() + ")").color(ChatColor.YELLOW).create());

				sender.sendMessage(new ComponentBuilder("Console:\t").color(ChatColor.AQUA)
						.append("(" + server.getConsoleHandlerThreads().size() + ")").color(ChatColor.AQUA).create());

                sender.sendMessage(new ComponentBuilder("Controller: ").color(ChatColor.GRAY)
                        .append("(" + server.getControllerHandlerThreads().size() + ")").color(ChatColor.GRAY).create());

                sender.sendMessage(baseLine);
				return;
			}
		} else { //args.length >= 1

			if(args[0].equalsIgnoreCase("thread") || args[0].equalsIgnoreCase("threads")) {
				//thread info
				if(args.length == 1) {

					HashMap<String, ServerHandlerThread> servers = server.getServerHandlerThreads();
					HashMap<String, ControllerHandlerThread> controller = server.getControllerHandlerThreads();
					ArrayList<ConsoleHandlerThread> consoles = server.getConsoleHandlerThreads();
					HashSet<ConnectThread> connectors = server.getConnectThreads();

					synchronized(sender) {

						sender.sendMessage(baseLine);

						sender.sendMessage(new ComponentBuilder("Threads running: ").color(ChatColor.RED).append("" + main.getRunningThreadCount())
								.color(ChatColor.RED).append("/").append("" + config.getInt(ZSend.MAX_THREADS_PATH)).create());

						if(server.isHosting()) sender.sendMessage(new ComponentBuilder("Listen thread running").color(ChatColor.GREEN).create());
						else sender.sendMessage(new ComponentBuilder("Listen thread stopped").color(ChatColor.RED).create());

						sender.sendMessage(baseLine);

						if(!connectors.isEmpty()) {

							sender.sendMessage(new ComponentBuilder("Connectors").color(ChatColor.GREEN).create());

							for(ConnectThread thread : connectors) {

								sender.sendMessage(new ComponentBuilder("  " + thread.getName().replace("ZSend ", "")).color(ChatColor.WHITE)
										.append(" | ").append(thread.getState().toString()).create());
							}
							sender.sendMessage(baseLine);
						}
						if(!servers.isEmpty()) {

							sender.sendMessage(new ComponentBuilder("Servers").color(ChatColor.YELLOW).create());

							for(String name : servers.keySet()) {

								sender.sendMessage(new ComponentBuilder("  " + servers.get(name).getName().replace("ZSend ", "")).color(ChatColor.WHITE)
										.append(" | ").append(servers.get(name).getState().toString()).create());
							}
							sender.sendMessage(baseLine);
						}
						if(!consoles.isEmpty()) {

							sender.sendMessage(new ComponentBuilder("Console").color(ChatColor.AQUA).create());

							for(ConsoleHandlerThread thread : consoles) {

								sender.sendMessage(new ComponentBuilder("  " + thread.getName().replace("ZSend ", "")).color(ChatColor.WHITE)
										.append(" | ").append(thread.getState().toString()).create());
							}
							sender.sendMessage(baseLine);
						}
						if(!controller.isEmpty()) {

							sender.sendMessage(new ComponentBuilder("Controller").color(ChatColor.GRAY).create());

							for(ControllerHandlerThread thread : controller.values()) {

								if(thread != null)
								sender.sendMessage(new ComponentBuilder("  " + thread.getName().replace("ZSend ", "")).color(ChatColor.WHITE)
										.append(" | ").append(thread.getState().toString()).create());
								else main.err("NULL");
							}
							sender.sendMessage(baseLine);
						}
					}
					return;
				}
				if(args.length == 2) {
					//Thread info for specified thread
					//TODO show thread with name args[1]
					sender.sendMessage(new ComponentBuilder("This hasn't been implemented yet!").color(ChatColor.RED).create());
					return;
				}
			} else if(args[0].equalsIgnoreCase("server") || args[0].equalsIgnoreCase("servers")) {
				//Server info
				if(args.length == 1) {
					//General server info
					HashMap<String, ServerHandlerThread> servers = main.getServer().getServerHandlerThreads();
					if(!servers.isEmpty()) {

						synchronized (sender) {

							sender.sendMessage(baseLine);

							sender.sendMessage(new ComponentBuilder("Server connections: ").color(ChatColor.GREEN)
									.append("(" + servers.size() + ")").color(ChatColor.GREEN).create());

							sender.sendMessage(baseLine);

							for(String name : servers.keySet()) {

								sender.sendMessage(new ComponentBuilder(name + ": ").color(ChatColor.GREEN)
										.append(servers.get(name).getName().replace("ZSend ", "")).color(ChatColor.WHITE).create());
							}

							sender.sendMessage(baseLine);
						}
						return;
					} else {

						sender.sendMessage(new ComponentBuilder("There are no server connections at this time").color(ChatColor.RED).create());
						return;
					}
				} else if(args.length == 2) {
					//Server info for specified server
					//TODO show server with name args[1]
					sender.sendMessage(new ComponentBuilder("This hasn't been implemented yet!").color(ChatColor.RED).create());
					return;
				}
			} else if(args[0].equalsIgnoreCase("console") || args[0].equalsIgnoreCase("consoles")) {
				//Console Info
				if(args.length == 1) {
					//General Console Info
					ArrayList<ConsoleHandlerThread> consoles = server.getConsoleHandlerThreads();
					if(!consoles.isEmpty()) {

						synchronized (sender) {

							sender.sendMessage(baseLine);

							sender.sendMessage(new ComponentBuilder("Console connections: ").color(ChatColor.AQUA)
									.append("(" + consoles.size() + ")").color(ChatColor.AQUA).create());

							sender.sendMessage(baseLine);

							for(int i = 0; i < consoles.size(); i++) {

								sender.sendMessage(new ComponentBuilder(i + ") ").color(ChatColor.AQUA)
										.append(consoles.get(i).getName().replace("ZSend ", "")).color(ChatColor.WHITE).create());
							}

							sender.sendMessage(baseLine);
						}
						return;
					} else {

						sender.sendMessage(new ComponentBuilder("There are no console connections at this time").color(ChatColor.RED).create());
						return;
					}
				} else if(args.length == 2) {
					//Console info for specified console
					//TODO show console with id args[1]
					sender.sendMessage(new ComponentBuilder("This hasn't been implemented yet!").color(ChatColor.RED).create());
					return;
				}
			} else if(args[0].equalsIgnoreCase("controller") || args[0].equalsIgnoreCase("controllers")) {

				if(args.length == 1) {
					//General Controller info
					HashMap<String, ControllerHandlerThread> controllers = main.getServer().getControllerHandlerThreads();
					if(!controllers.isEmpty()) {

						synchronized (sender) {

							sender.sendMessage(baseLine);

							sender.sendMessage(new ComponentBuilder("Controller connections: ").color(ChatColor.GRAY)
									.append("(" + controllers.size() + ")").color(ChatColor.GRAY).create());

							sender.sendMessage(baseLine);

							for(ControllerHandlerThread thread : controllers.values()) {

								sender.sendMessage(new ComponentBuilder(thread.getControllerName() + ": ").color(ChatColor.GRAY)
										.append(thread.getName().replace("ZSend ", "")).color(ChatColor.WHITE).create());
							}

							sender.sendMessage(baseLine);
						}
					}
					return;
				} else if(args.length == 2) {

					//TODO show controller with name args[1]
					sender.sendMessage(new ComponentBuilder("This hasn't been implemented yet!").color(ChatColor.RED).create());
					return;
				}
			} else if(args[0].equalsIgnoreCase("stop")) {
				//Stop server hosting
				if(args.length == 1) {
					//Stop server hosting without dropping connections
					if(server.isHosting()) {

						server.stopHosting();
						sender.sendMessage(new ComponentBuilder("Stopped hosting").color(ChatColor.YELLOW).create());
						return;
					} else {

						sender.sendMessage(new ComponentBuilder("Can't stop to host, currently not hosting").color(ChatColor.RED).create());
						return;
					}
				} else if(args.length == 2 && args[1].equalsIgnoreCase("drop")) {
					//Stop server hosting with dropping all connections
					if(server.isHosting()) {

						server.stopHosting();
						server.disconnectAll(true);
						sender.sendMessage(new ComponentBuilder("Stopped hosting and dropped all connections").color(ChatColor.YELLOW).create());
						return;
					} else {

						server.disconnectAll(true);
						sender.sendMessage(new ComponentBuilder("Can't stop to host, currently not hosting, dropping all connections").color(ChatColor.RED).create());
						return;
					}
				}
			} else if(args[0].equalsIgnoreCase("host") || args[0].equalsIgnoreCase("start")) {

				if(args.length == 1) {
					//Start server hosting
					if(!server.isHosting()) {

						server.startHosting(config.getInt(ZSend.PORT_PATH));
						sender.sendMessage(new ComponentBuilder("Started hosting").color(ChatColor.YELLOW).create());
						return;
					} else {

						sender.sendMessage(new ComponentBuilder("Can't start to host, already hosting").color(ChatColor.RED).create());
						return;
					}
				}
			} else if(args[0].equalsIgnoreCase("drop") && args.length >= 2) { //drop all or specified connections
				//Drop connection(s)
				if(args[1].equalsIgnoreCase("all")) {

					if(args.length == 2) {
						//Drop all connections
						sender.sendMessage(new ComponentBuilder("Disconnecting all...").color(ChatColor.YELLOW).create());
						server.disconnectAll(true);
						return;
					} else if(args.length == 3) {
						//Drop either all server, console or controller connections
						if(args[2].equalsIgnoreCase("server")) {

							HashMap<String, ServerHandlerThread> map = server.getServerHandlerThreads();
							for(ServerHandlerThread thread : map.values()) thread.interrupt();

							sender.sendMessage(new ComponentBuilder("Successfully dropped all server connections").color(ChatColor.YELLOW).create());
						} else if(args[2].equalsIgnoreCase("console")) {

							for(ConsoleHandlerThread thread : server.getConsoleHandlerThreads()) thread.interrupt();

							sender.sendMessage(new ComponentBuilder("Successfully dropped all Console connections").color(ChatColor.YELLOW).create());
						} else if(args[2].equalsIgnoreCase("controller")) {

							HashMap<String, ControllerHandlerThread> map = server.getControllerHandlerThreads();
							for(ControllerHandlerThread thread : map.values()) thread.interrupt();

							sender.sendMessage(new ComponentBuilder("Successfully dropped all server connections").color(ChatColor.YELLOW).create());
						}
						sender.sendMessage(new ComponentBuilder("Successfully dropped all console connections").color(ChatColor.YELLOW).create());
					}
				} else if(args.length == 3) {

					if(args[1].equalsIgnoreCase("server")) {
						//Drop specified server connection
						ServerHandlerThread thread = server.getServerHandlerThreads().get(args[2]);
						if(thread != null)  {

							thread.sendCommand(new ZCommand(null, thread.getServerName(), null, "zr disconnect"));
							thread.interrupt();
							sender.sendMessage(new ComponentBuilder("Successfully dropped Server \"" + args[2].toLowerCase()).color(ChatColor.YELLOW).append("\"").create());
							return;
						} else {

							sender.sendMessage(new ComponentBuilder("Couldn't drop connection, server isn't connected or doesn't exist").color(ChatColor.RED).create());
							return;
						}
					} else if(args[1].equalsIgnoreCase("console")) {
						//Drop specified console connection
						ConsoleHandlerThread thread;
						try {

							int id = Integer.parseInt(args[2]);
							thread = server.getConsoleHandlerThreads().get(id);
							thread.interrupt();
							sender.sendMessage(new ComponentBuilder("Successfully dropped Console \'" + id).color(ChatColor.YELLOW).append("\'").create());
							return;
						} catch(NumberFormatException ex) {

							sender.sendMessage(new ComponentBuilder("Couldn't drop connection, ID of the console has to be a number").color(ChatColor.RED).create());
							return;
						} catch(IndexOutOfBoundsException ex) {

							sender.sendMessage(new ComponentBuilder("Couldn't drop connection, illegal ID").color(ChatColor.RED).create());
							return;
						}
					} else if(args[1].equalsIgnoreCase("controller")) {
						//Drop specified controller connection
						ControllerHandlerThread thread = server.getControllerHandlerThreads().get(args[2]);
						if(thread != null)  {

							thread.interrupt();
							sender.sendMessage(new ComponentBuilder("Successfully dropped controller \"" + args[2].toLowerCase()).color(ChatColor.YELLOW).append("\"").create());
							return;
						} else {

							sender.sendMessage(new ComponentBuilder("Couldn't drop connection, controller isn't connected or doesn't exist").color(ChatColor.RED).create());
							return;
						}
					}
				}
			} else if(args[0].equalsIgnoreCase("connect")) {

                if (!server.isMaster()) {

                    sender.sendMessage(new ComponentBuilder("Server is master, cannot connect").color(ChatColor.RED).create());
                    return;
                } else if (server.getControllerHandlerThreads().size() >= 1) {

                    sender.sendMessage(new ComponentBuilder("Already connected to master server").color(ChatColor.RED).create());
                    return;
                }

                server.connectToMaster();
                sender.sendMessage(new ComponentBuilder("Connecting to master server...").color(ChatColor.YELLOW).create());
                return;
			} else if(args[0].equalsIgnoreCase("history") && args.length == 2) {

				if(args[1].equalsIgnoreCase("command") || args[1].equalsIgnoreCase("commands")) {
					//Show command history
					//TODO add possibility to specify the number of history entries shown
					HashMap<ZCommand, Integer> history = main.getCommandHistory();

					if(!history.isEmpty()) {

						synchronized (sender) {

							sender.sendMessage(baseLine);

							for(ZCommand cmd : history.keySet()) {

                                ChatColor color;

                                switch(history.get(cmd)) {

                                    case ZAcknowledge.SUCCESS:

                                        color = ChatColor.GREEN;
                                        break;
                                    case ZAcknowledge.ERROR:

                                        color = ChatColor.RED;
                                        break;
                                    case ZAcknowledge.TIMEOUT:

                                        color = ChatColor.BLUE;
                                        break;
                                    default:

                                        color = ChatColor.GRAY;
                                }

								sender.sendMessage(new ComponentBuilder(cmd.toString()).color(color).create());
							}

							sender.sendMessage(baseLine);

							sender.sendMessage(new ComponentBuilder("History entry size: ").append("" + history.size()).color(ChatColor.AQUA).create());

							sender.sendMessage(baseLine);
							return;
						}
					} else {

						sender.sendMessage(new ComponentBuilder("There is no command history to show").color(ChatColor.RED).create());
						return;
					}
				} else if(args[1].equalsIgnoreCase("thread") || args[1].equalsIgnoreCase("threads")) {
					//Show thread history
					//TODO show thread history

					sender.sendMessage(new ComponentBuilder("This hasn't been implemented yet!").color(ChatColor.RED).create());
					return;
				}
			}
		}
		//if command was not correctly entered
		sender.sendMessage(new ComponentBuilder("Illegal command Syntax!").color(ChatColor.RED).create());
	}
}
