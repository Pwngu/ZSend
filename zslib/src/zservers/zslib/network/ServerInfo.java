package zservers.zslib.network;

import java.io.Serializable;

public class ServerInfo implements Serializable {

	private static final long serialVersionUID = -6735257614236868917L;

	public static final int CONTROLLER = 1;
	public static final int SERVER = 2;

	public final Long timestamp;

	public final String name;

	public final Integer type;

	public ServerInfo(String serverName, int serverType) {

		timestamp = System.currentTimeMillis();
		this.name = serverName;
		this.type = serverType;
	}
}
