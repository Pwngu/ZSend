package zservers.zreceive;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import zservers.zlib.main.ZBukkit;
import zservers.zlib.main.ZLib;

public final class ZReceive extends ZBukkit {

	public static final String LOGLEVEL_PATH 		= "loglevel";
	public static final String PORT_PATH 			= "port";
	public static final String IP_PATH 				= "ip";
	public static final String AUTO_RECONNECT_PATH 	= "autoreconnect";

	private static ZReceive instance;
	private ConnectionHandlerThread thread;

	private boolean disconnected;

	public void onLoad() {

		instance = this;
		disconnected = false;
		this.saveDefaultConfig();

        inf("Using ZLib " + ZLib.getInstance().getVersion());
	}

	public void onEnable() {

		thread = new ConnectionHandlerThread();
		thread.start();
	}

	public void onDisable() {

		if(thread != null) {

			thread.skipRestart();
			thread.interrupt();
		}
	}

	@Override
	public int getLogLevel() {

		return getConfig().getInt(LOGLEVEL_PATH);
	}

	@Override
	public String getServerName() {

		return Bukkit.getServerName();
	}

	public static ZReceive getInstance() {

		return ZReceive.instance;
	}

	public void handlerEnded(boolean restart) {

		instance.dbg("Handler ended, " + (restart ? "restarting..." : "killing..."));

		if(restart) {

			try {

				Thread.sleep(5000);
			} catch(InterruptedException ex) {

				instance.dbg("Reconnect Thread interrupted");
			}

			instance.thread = new ConnectionHandlerThread();
			instance.thread.start();
		} else {

			instance.thread = null;
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		if(command.getName().equalsIgnoreCase("zr")) {

			if(args.length == 0) {

				String threadstate = ChatColor.GREEN + "Everything OK";
				if(thread == null && disconnected) threadstate = ChatColor.RED + "Preventing reconnect";
				else if(thread == null && !getConfig().getBoolean(AUTO_RECONNECT_PATH)) threadstate = ChatColor.RED + "Auto-reconnect disabled and connect thread dead!";
				else if(thread == null || !thread.isConnected()) threadstate = ChatColor.YELLOW + "Trying to reconnect...";

				synchronized (sender) {

					StringBuilder sb = new StringBuilder(20);
					for(int i = 0; i < 3; i++) sb.append(ChatColor.GOLD).append("--").append(ChatColor.BLUE).append("--");
					sb.append(ChatColor.GOLD).append("--");



//					sender.sendMessage(ChatColor.GOLD + "ZReceive " + ChatColor.RED + getDescription().getVersion());
					sender.sendMessage(sb.toString());
					sender.sendMessage(((thread == null || !thread.isConnected()) ? (ChatColor.RED + "NOT CONNECTED") : (ChatColor.GREEN + "CONNECTED")));
					sender.sendMessage(threadstate);
					sender.sendMessage(sb.toString());
					return true;
				}
			}
			if(args.length == 1) {

				switch(args[0].toLowerCase()) {

				case "reconnect":
					if(thread != null) {
						thread.interrupt();
						sender.sendMessage(ChatColor.YELLOW + "Reconnecting...");
						return true;
					} else return false;

				case "drop":
				case "disconnect":
					if(thread != null) {
						thread.interrupt();
						thread.skipRestart();
						disconnected = true;
						sender.sendMessage(ChatColor.YELLOW + "Disconnecting...");
						return true;
					} else return false;

				case "connect":
					if(thread == null) {
						thread = new ConnectionHandlerThread();
						thread.start();
						disconnected = false;
						sender.sendMessage(ChatColor.YELLOW + "Connecting...");
						return true;
					} else return false;
				}
			}
		}
		return false;
	}
}