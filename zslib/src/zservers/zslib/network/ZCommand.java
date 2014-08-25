package zservers.zslib.network;

import java.io.Serializable;

import zservers.zlib.main.ZLib;

public class ZCommand implements Serializable, Id {

	private static final long serialVersionUID = 6439307713279984873L;

	@SuppressWarnings("unused")
	private static final ZLib main = ZLib.getInstance();

	public static final String BCSTRING = "ZSBC";

	private transient Connector localConnection;

	public final Long id;
	public final String controller;
	public final String server;
	public final String executor;
	public final String command;

	public final boolean bcExecutor;
	public final boolean bcServer;

	public ZCommand(String line) throws Exception {

		this(line, null);
	}

	public ZCommand(String line, Connector connector) throws Exception {

		this(line, -1, connector);
	}

	public ZCommand(String line, int consoleID, Connector connector) throws Exception {

		if(line == null) throw new IllegalArgumentException("Line is null");
		if(!line.contains(":")) throw new Exception("Syntax error in command line, missing \':\'");

		//search for first un-escaped ':'
		int cmdIn = 0;
		do {

			cmdIn++;
			cmdIn = line.indexOf(":", cmdIn);
		} while(line.charAt(cmdIn - 1) == '\\');

		//search for first un-escaped '#'
		if(line.contains("#") && !line.contains("\\#")) {

			int idIn = 0;
			do {

				idIn++;
				idIn = line.indexOf("#", idIn);
			} while (line.charAt(idIn - 1) == '\\');

			try {

				id = Long.parseLong(line.substring(idIn + 1, cmdIn));
			} catch (NumberFormatException ex) {

				throw new Exception("1:Illegal ID");
			}
		} else {

			id = System.nanoTime();
		}

		String options = line.substring(0, cmdIn);
		command = line.substring(cmdIn + 1);

		if(options.contains("(") && options.contains(")"))
			executor = options.substring(options.indexOf('(') + 1, options.indexOf(')'));
		else executor = null;

		if(options.contains("[") && options.contains("]"))
			server = options.substring(options.indexOf('[') + 1, options.indexOf(']'));
		else server = null;

		if(options.contains("{") && options.contains("}"))
			controller = options.substring(options.indexOf('{') + 1, options.indexOf('}'));
		else controller = null;

		bcExecutor = (executor != null && executor.equals(BCSTRING));
		bcServer = (server != null && server.equals(BCSTRING));
	}

	public ZCommand(ZCommand command) {

		this(command.controller, command.server, command.executor, command.command);
	}

	public ZCommand(String controller, String server, String executor, String command) {

		this.id = System.nanoTime();

		this.controller = controller;
		this.server = server;
		this.executor = executor;
		this.command = command;

		bcExecutor = (executor != null && executor.equals(BCSTRING));
		bcServer = (server != null && server.equals(BCSTRING));
	}

	public Connector getLocalConnection() {

		return localConnection;
	}

	public void setLocalConnection(Connector connection) {

		this.localConnection = connection;
	}

	public boolean sameCommand(ZCommand command) {

		return this.controller.equals(command.controller) && this.server.equals(command.server)
				&& this.executor.equals(command.executor) && this.command.equals(command.command);
	}

	public String toString() {

		StringBuilder sb = new StringBuilder();
		if(controller != null) sb.append("{").append(controller).append("}");
		if(server != null) sb.append("[").append(server).append("]");
		if(executor != null) sb.append("(").append(executor).append(")");
		sb.append("#").append(id);
		sb.append(":").append(command);

		return sb.toString();
	}

	public String toShortString() {

		StringBuilder sb = new StringBuilder(11);
		if(controller != null) sb.append("{").append(controller).append("}");
		if(server != null) sb.append("[").append(server).append("]");
		if(executor != null) sb.append("(").append(executor).append(")");
		sb.append(":").append(command);

		return sb.toString();
	}

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ZCommand zCommand = (ZCommand) o;

        return !(id != null ? !id.equals(zCommand.id) : zCommand.id != null);
    }

    @Override
    public int hashCode() {

        return id != null ? id.hashCode() : 0;
    }

    @Override
    public long getID() {

        return id;
    }
}
