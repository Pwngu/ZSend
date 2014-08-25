package zservers.zsend.main;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.PluginManager;
import zservers.zlib.main.ZBungeeConfig;
import zservers.zlib.main.ZLib;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;
import zservers.zsend.command.Dispatch;
import zservers.zsend.command.ZSendInfo;
import zservers.zsend.network.ConnectThread;
import zservers.zsend.network.ControllerHandlerThread;
import zservers.zsend.network.Server;
import zservers.zsend.network.ServerHandlerThread;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public class ZSend extends ZBungeeConfig {

	public static final String PW_PATH 				= "password";
	public static final String PORT_PATH 			= "port";
	public static final String MAX_THREADS_PATH 	= "maxconnections";
	public static final String LOGLEVEL_PATH 		= "loglevel";
	public static final String CONSOLE_IPS_PATH 	= "consoleips";
	public static final String SERVER_IPS_PATH 		= "serverips";
	public static final String CONTROLLER_IPS_PATH 	= "controllerips";
	public static final String MASTER_IP_PATH 	    = "masterip";
	public static final String TIMEOUT_PATH 		= "timeout";
	public static final String ADMIN_PERM_PATH 		= "admin";
	public static final String SERVER_ID_PATH 		= "serverid";

	private static final Object THREAD_LOCK = new Object();

	private static ZSend instance;

	private volatile HashMap<ZCommand, Integer> commandHistory;

	private volatile int threads;

	private Server server;

	public ZSend() {

		super("ZSend");

		if(instance != null) throw new RuntimeException("ZSend already instantiated");
		instance = this;

		commandHistory = new HashMap<>();
		threads = 0;
	}

	@Override
	public void onLoading() {

		saveDefaultConfig();

        inf("Using ZLib " + ZLib.getInstance().getVersion());

        server = new Server();
		registerCommands();
	}

	@Override
	public void onEnable() {

		server.startHosting(getConfig().getInt(PORT_PATH));
	}

	@Override
	public void onDisable() {

		server.stopHosting();
		server.disconnectAll(false);
	}

	public static ZSend getInstance() {

		return instance;
	}

	@Override
	public int getLogLevel() {

		return getConfig().getInt(LOGLEVEL_PATH);
	}

	@Override
	public String getServerName() {

		return getConfig().getString(SERVER_ID_PATH);
	}

	public Server getServer() {

		return server;
	}

	public int getRunningThreadCount() {

		return threads;
	}

	public boolean startThread(Thread thread) {

		synchronized (THREAD_LOCK) {

			if(threads >= getConfig().getInt(MAX_THREADS_PATH)) {

				if(thread instanceof ConnectThread) {

					wrn("Reached Thread cap: \'" + getConfig().getInt(MAX_THREADS_PATH) + "\' dropping " + thread.getName());
					thread.interrupt();
				} else {

					Thread current = Thread.currentThread();
					wrn("Reached Thread cap: \'" + getConfig().getInt(MAX_THREADS_PATH)
							+ "\' dropping current " + thread.getName() + " and " + thread.getName());
					current.interrupt();
					thread.interrupt();
				}
				return false;
			}
			dbg(thread.getName() + " started");
			thread.start();
			threads++;
			return true;
		}
	}

	public void endThread(Thread thread) {

		synchronized (THREAD_LOCK) {

			dbg("Interrupting " + thread.getName());

			if(thread instanceof ConnectThread) server.connected((ConnectThread) thread);
			else if(thread instanceof ControllerHandlerThread && !server.isMaster())
				server.connectToMaster();
			else thread.interrupt();
			threads--;
		}
	}

	public HashMap<ZCommand, Integer> getCommandHistory() {

		return commandHistory;
	}

	public ZAcknowledge dispatchCommand(ZCommand command) {

		inf("Dispatching command: " + command.toString());

		ZAcknowledge acknowledge = null;

		commandHistory.put(command, -1);

		if(command.controller == null || command.controller.equals(getInstance().getConfig().getString(SERVER_ID_PATH)) ||
                (command.controller.equals("ZSMaster") && server.isMaster())) {
			//true if the command is for this server
			if(command.server == null || command.bcServer) {

				if(command.executor == null || command.bcExecutor) {

					if(!ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), command.command)) {

						wrn("Error dispatching command, #" + command.id + " Bungee dispatch failed");
                        commandHistory.put(command, ZAcknowledge.ERROR);
						acknowledge = new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, Bungee dispatch failed");
					} else {

						dbg("Successfully dispatched command #" + command.id);
                        commandHistory.put(command, ZAcknowledge.SUCCESS);
						acknowledge = new ZAcknowledge(command, ZAcknowledge.SUCCESS, "Successfully dispatched command");
					}
				} else { //controller == null / server == null / sender != null

					ProxiedPlayer player = ProxyServer.getInstance().getPlayer(command.executor);

					if(player != null) {

						synchronized (player) {

							String adminperm = getConfig().getString(ADMIN_PERM_PATH);
							player.setPermission(adminperm, true);
							if(!ProxyServer.getInstance().getPluginManager().dispatchCommand(player, command.command)) {

								wrn("Error dispatching command #" + command.id);
                                commandHistory.put(command, ZAcknowledge.ERROR);
								acknowledge = new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, Bungee dispatch failed");
								player.setPermission(adminperm, false);
							} else {

								dbg("Successfully dispatched command");
                                commandHistory.put(command, ZAcknowledge.SUCCESS);
								acknowledge = new ZAcknowledge(command, ZAcknowledge.SUCCESS, "Successfully dispatched command");
								player.setPermission(adminperm, false);
							}
						}
					} else { //controller == null / server == null / player == null

						wrn("Error dispatching command, couldn't find player");
                        commandHistory.put(command, ZAcknowledge.ERROR);
						acknowledge = new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, couldn't find player");
					}
				}
			} else { //controller == null / server != null

				if(!getServer().getServerHandlerThreads().containsKey(command.server)) {

					wrn("Error dispatching command, server isn't connected or doesn't exist");
                    commandHistory.put(command, ZAcknowledge.ERROR);
					acknowledge = new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, server isn't connected or doesn't exist");
				} else {

					inf("Forwarding #" + command.id + " to " + command.server);

					ServerHandlerThread thread = getServer().getServerHandlerThreads().get(command.server);

					thread.sendCommand(command);

					try {

						acknowledge = thread.getAcknowledge(command);
					} catch(InterruptedException ex) {

						err(ex, "Interrupted Exception while waiting for command Acknowledge #" + command.id);
                        commandHistory.put(command, ZAcknowledge.TIMEOUT);
					} catch(TimeoutException ex) {

                        wrn(ex, "Timeout Exception while waiting for command Acknowledge #" + command.id);
                        commandHistory.put(command, ZAcknowledge.TIMEOUT);
                    }
				}
			}
		} else { //controller != null / no broadcast

            if(!getServer().getControllerHandlerThreads().containsKey(command.controller)) {

                wrn("Error dispatching command, controller isn't connected or doesn't exist");
                commandHistory.put(command, ZAcknowledge.ERROR);
                acknowledge = new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, controller isn't connected or doesn't exist");
            } else {

                inf("Forwarding #" + command.id + " to " + command.controller);

                ControllerHandlerThread thread = getServer().getControllerHandlerThreads().get(command.controller);

                thread.sendCommand(command);

                try {

                    acknowledge = thread.getAcknowledge(command);
                } catch (InterruptedException ex) {

                    wrn(ex, "Interrupted Exception while waiting for command Acknowledge #" + command.id);
                    commandHistory.put(command, ZAcknowledge.TIMEOUT);
                } catch(TimeoutException ex) {

                    wrn(ex, "Timeout Exception while waiting for command Acknowledge #" + command.id);
                    commandHistory.put(command, ZAcknowledge.TIMEOUT);
                }
            }
        }

		if(command.bcServer) {

			if(server.getServerHandlerThreads().size() >= 1) {

				inf("Server Broadcasting...");
				acknowledge = broadcastServer(command);
			}
		}

		return acknowledge;
	}

	private ZAcknowledge broadcastServer(ZCommand command) {

        ZAcknowledge acknowledge = null;

        for (ServerHandlerThread thread : server.getServerHandlerThreads().values()) {

            thread.sendCommand(command);
        }

        int timeout = getConfig().getInt(TIMEOUT_PATH);

        for (int loops = 0; loops * 100 < timeout; loops++) {

            for (ServerHandlerThread thread : server.getServerHandlerThreads().values()) {

                if (thread.hasAcknowledge(command)) {

                    try {

                        acknowledge = thread.getAcknowledge(command);
                    } catch (InterruptedException ex) {

                        wrn(ex, "Interrupted Exception while trying to get broadcast acknowledge");
                        commandHistory.put(command, ZAcknowledge.TIMEOUT);
                    } catch (TimeoutException ignored) {
                    }
                }
            }

            try {

                Thread.sleep(100);
            } catch (InterruptedException ex) {

                wrn(ex, "Interrupted Exception while trying to get broadcast acknowledge");
                commandHistory.put(command, ZAcknowledge.TIMEOUT);
            }
        }

        for (ServerHandlerThread thread : server.getServerHandlerThreads().values()) {

            thread.abortWaitForAcknowledge(command);
        }
        return acknowledge;
	}

	private void registerCommands() {

		PluginManager manager = ProxyServer.getInstance().getPluginManager();

		manager.registerCommand(this, new ZSendInfo());
		manager.registerCommand(this, new Dispatch());
	}
}
