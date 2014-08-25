package zservers.zslib.network;

import zservers.zlib.utilities.Utilities;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class Authenticator implements Serializable, Id {

	private static final long serialVersionUID = -6984458938158192890L;

    private final Long id;

	private Long timestamp;
	private transient Boolean okTimestamp;

	private String password;

	public final ServerInfo serverInfo;
	public final int connectionType;

	private transient Connector connection;

	public Authenticator(ServerInfo info, int connectionType) {

		this(null, info, connectionType);
	}

    public Authenticator(String password, ServerInfo info, int connectionType) {

        this.id = System.currentTimeMillis();

		this.password = Utilities.md5(password);
		this.okTimestamp = true;

		if(connectionType == Connector.INTERN_CONSOLE_CONNECTION) {

			this.serverInfo = new ServerInfo(null, 0);
			this.connectionType = connectionType;
		} else {

			this.serverInfo = info;
			this.connectionType = connectionType;
		}
	}

	public void setLocalConnection(Connector connection) {

		this.connection = connection;
	}

	public Connector getLocalConnection() {

		return connection;
	}

	public boolean isLegal() {

		return okTimestamp;
	}

	public boolean checkPassword(String passwordHash) {

		if(!okTimestamp) throw new SecurityException("Illegal Timestamp of Authenticator");
        if(password == null && passwordHash == null) return true;
        return password.equalsIgnoreCase(passwordHash);
    }

	private void writeObject(ObjectOutputStream output) throws IOException {

		timestamp = System.currentTimeMillis();
		output.defaultWriteObject();
	}

	private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {

		input.defaultReadObject();
        okTimestamp = System.currentTimeMillis() - timestamp <= 60000;
	}


    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Authenticator that = (Authenticator) o;

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {

        return id.hashCode();
    }

    @Override
    public long getID() {

        return id;
    }
}
