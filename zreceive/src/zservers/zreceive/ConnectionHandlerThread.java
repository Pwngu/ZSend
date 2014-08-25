package zservers.zreceive;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import zservers.zslib.network.Connector;
import zservers.zslib.network.Authenticator;
import zservers.zslib.network.ServerInfo;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;

import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

public class ConnectionHandlerThread extends Thread {

	private static ZReceive main = ZReceive.getInstance();

	private Connector connection;
	private FileConfiguration config;

	private volatile boolean skipRestart;

	public ConnectionHandlerThread() {

		config = ZReceive.getInstance().getConfig();
		this.setName("ZReceive Connection Handler Thread");

		skipRestart = false;
	}

	public void run() {

		try {

			connect();

			if(connection == null || connection.isClosed()) {

                main.dbg("Connection failed");
				main.handlerEnded(!skipRestart && config.getBoolean(ZReceive.AUTO_RECONNECT_PATH));
				return;
			}

			while(!isInterrupted()) {

				ZCommand command;
				Object obj = connection.receive();
				if(obj == null) {

					break;
				} else if(obj instanceof String) {

					main.inf((String) obj);
					continue;
				} else if(obj instanceof ZCommand) {

					command = (ZCommand) obj;
				} else continue;

				if(command.executor == null) {

					if(!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.command)) {

						main.wrn("Error whilst dispatching command " + command.toShortString());
						connection.send(new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, Bukkit dispatch failed"));
					} else {

						main.dbg("Successfully dispatched command " + command.toShortString());
						connection.send(new ZAcknowledge(command, ZAcknowledge.SUCCESS, "Successfully dispatched command"));
					}
				} else {

					if(command.bcExecutor) {

						Player[] players = Bukkit.getOnlinePlayers();

						boolean ok = false;
						for(Player player : players)
							if(Bukkit.dispatchCommand(player, command.command)) ok = true;

						connection.send(new ZAcknowledge(command, ok ? ZAcknowledge.SUCCESS : ZAcknowledge.ERROR, ok ? "Successfully dispatched command" : "Error dispatching command"));
					} else {

						Player player = Bukkit.getPlayer(command.executor);
						if(player == null) {

							main.wrn("Error whilst dispatching command, couldn't find player: " + command.toShortString());
							connection.send(new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, couldn't find player"));
						} else {

							if(!Bukkit.dispatchCommand(player, command.command)) {

								main.wrn("Error whilst dispatching command " + command.toShortString());
								connection.send(new ZAcknowledge(command, ZAcknowledge.ERROR, "Error dispatching command, Bukkit dispatch failed"));
							} else {

								main.dbg("Successfully dispatched command " + command.toShortString());
								connection.send(new ZAcknowledge(command, ZAcknowledge.SUCCESS, "Successfully dispatched command"));
							}
						}
					}
				}
			}
		} catch(Exception ex) {

			main.err(ex, "Exception in Handler Thread");
		}
		if(connection != null) connection.drop();
		main.handlerEnded(!skipRestart && config.getBoolean(ZReceive.AUTO_RECONNECT_PATH));
	}

	private void connect() throws InterruptedException {

		int loops = 0;
		do {

			try {

				loops++;
				if(loops != 1) {

					Thread.sleep(5000);
				}

				main.dbg("Starting to connect");
                Authenticator auth = new Authenticator(new ServerInfo(Bukkit.getServerName(), ServerInfo.SERVER), Connector.SERVER_CONNECTION);
				connection = new Connector(new Socket(config.getString(ZReceive.IP_PATH), config.getInt(ZReceive.PORT_PATH)), "Connection Handler");
				connection.send(auth);

				ZAcknowledge acknowledge = connection.receive();

                if(acknowledge == null) {

                    main.err("Didn't get correct acknowledge" + (config.getBoolean(ZReceive.AUTO_RECONNECT_PATH) ? ", retrying in 5 seconds..." : ""));
                    continue;
                }

				if(!acknowledge.isAcknowledgeFor(auth)) {

					main.err("Dropping connection");
					connection.drop();
					return;
				} else if(acknowledge.state == ZAcknowledge.SUCCESS) {

					main.inf("Successfully connected to BungeeServer");
				} else {

					main.err("Error whilst authenticating");
					main.err(acknowledge.state + " >> " + acknowledge.message);
                    skipRestart();
					connection.drop();
					return;
				}

				if((loops == 1 || loops % 12 == 0) && connection.isClosed()) main.err("Couldn't connect to Bungee server");

			} catch(UnknownHostException ex) {

                main.dbg("Couldn't find host whilst connecting" + (config.getBoolean(ZReceive.AUTO_RECONNECT_PATH) ? ", retrying in 5 seconds..." : ""));

            } catch (ConnectException ex) {

                main.dbg("Couldn't connect to server"  + (config.getBoolean(ZReceive.AUTO_RECONNECT_PATH) ? ", retrying in 5 seconds..." : ""));
            } catch(Exception ex) {

				main.dbg(ex, "Error whilst connecting to Bungee server"  + (config.getBoolean(ZReceive.AUTO_RECONNECT_PATH) ? ", retrying in 5 seconds..." : ""));
			}
		} while((connection == null || connection.isClosed()) && !isInterrupted() && config.getBoolean(ZReceive.AUTO_RECONNECT_PATH) && !skipRestart);
	}

	public void skipRestart() {

		skipRestart = true;
	}

	public boolean isConnected() {

		return connection != null;
	}

	public void interrupt() {

		if(connection != null) connection.drop();
		super.interrupt();
	}
}
