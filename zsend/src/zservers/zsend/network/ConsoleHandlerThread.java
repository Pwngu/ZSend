package zservers.zsend.network;

import zservers.zsend.main.ZSend;
import zservers.zslib.network.Connector;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;

public class ConsoleHandlerThread extends Thread {

	private static ZSend main = ZSend.getInstance();

	private Connector connection;
	private Server server;

	public ConsoleHandlerThread(Connector connection, Server server) {

		this.connection = connection;
		this.server = server;
		this.setName("ZSend Console Handler Thread: " + connection.getSocket().getLocalAddress().toString());
	}

	public void run() {

		try {

			boolean repeat;
			do {

				if(connection.getType() == Connector.INTERN_CONSOLE_CONNECTION) {

					repeat = handleInternConnection();
				} else if(connection.getType() == Connector.EXTERN_CONSOLE_CONNECTION) {

					repeat = handleExternConnection();
				} else {

					throw new RuntimeException("Illegal ConnectionType");
				}
			} while(!isInterrupted() && repeat);

		} catch(Exception ex) {

			main.err(ex, "Exception in " + this.getName());
		} catch(ThreadDeath death) {

			server.disconnect(connection);
			main.endThread(this);
			throw death;
		}
		server.disconnect(connection);
		main.endThread(this);
	}

	private boolean handleInternConnection() throws Exception {

		ZCommand command = connection.receive();

		if(command == null) {

			return false;
		} else {

			if(!checkForDangerousCommand(command)) return true;

			connection.send(main.dispatchCommand(command));
			return true;
		}
	}

	private boolean handleExternConnection() throws Exception {

		String line = connection.recvString();

		if(line == null) {

			return false;
		} else if(line.equals("")) return true;

		ZCommand command;
		try {

			command = new ZCommand(line);
			command.setLocalConnection(connection);
		} catch(Exception ex) {

			main.wrn(ex, "Excption whilst instantiating command");
			connection.send("1:" + ex.getMessage());
			return true;
		}

		if(!checkForDangerousCommand(command)) return true;

//		server.disconnect(connection);
		ZAcknowledge result = main.dispatchCommand(command);
		connection.send(result.state + ":" + result.message);
		return true;
	}

	private boolean checkForDangerousCommand(ZCommand command) {

		if(command.command.toLowerCase().startsWith("zs stop") || command.command.toLowerCase().startsWith("drop console") || command.command.equalsIgnoreCase("drop all console")) {

			main.dbg("Potentially dangerous command, asking user...");
			String qst;
			if(command.command.toLowerCase().startsWith("zs stop")) qst = "2:Executing this command will disconnect you from the server and prevent you from reconnecting, are you sure?";
			else if(command.command.toLowerCase().startsWith("zs drop console")) qst = "2:Executing this command could disconnect you from the server, are you sure?";
			else if(command.command.equalsIgnoreCase("zs drop all console")) qst = "2:Executing this command will prevent you from reconnecting, if you disconnect, are you sure?";
			else {

				main.wrn("Error in Potentially dangerous command...");
				return false;
			}
			connection.send(qst);

			String answ = connection.recvString();

			if(answ == null) {

				return false;
			} else if(!answ.equalsIgnoreCase("y") && !answ.equalsIgnoreCase("yes")) {

				main.dbg("Aborting command executing, aborted by user");
				connection.send("1:Aborting command executing, aborted by user");
				return false;
			}
			main.dbg("Executing command anyway");
			return true;
		} else {

			return true;
		}
	}

	public void interrupt() {

		if(connection != null) connection.drop();
		super.interrupt();
	}

	public boolean handles(Connector connection) {

		return this.connection.equals(connection);
	}

	public Connector getConnection() {

		return connection;
	}
}
