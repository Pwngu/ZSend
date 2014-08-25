package zservers.zslib.network;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import zservers.zlib.main.ZLib;

public class Connector {

	private static ZLib main = ZLib.getInstance();

	private final Object READING = new Object();

	public static final int UNDEFINED_CONNECTION = 0;
	public static final int SERVER_CONNECTION = 1;
	public static final int INTERN_CONSOLE_CONNECTION = 2;
	public static final int EXTERN_CONSOLE_CONNECTION = 3;
	public static final int CONTROLLER_CONNECTION = 4;

	private ObjectInputStream in;
	private ObjectOutputStream out;

	private Socket socket;

	private String name;

	private volatile int type;
	private volatile boolean isClosed;

	public Connector(Socket socket) {

		this(socket, "UNSET");
	}

	public Connector(Socket socket, String name) {

		this(socket, UNDEFINED_CONNECTION, name);
	}

	public Connector(Socket socket, int connectionType) {

		this(socket, connectionType, "UNSET");
	}

	public Connector(Socket socket, int connectionType, String name) {

		try {

			this.socket = socket;
			setType(connectionType);

			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());

			this.name = name;
		} catch(Exception ex) {

			throw new RuntimeException("Couldn't instantiate Connector", ex);
		}
	}

	public Connector(Connector connection) {

		this(null, connection.type, connection.name);

		try {

			this.socket = new Socket(connection.socket.getInetAddress(), connection.socket.getPort());
		} catch(Exception ex) {

			main.err(ex, "Exception whilst cloning Connector");
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T receive() {

		if(type == EXTERN_CONSOLE_CONNECTION) throw new RuntimeException("Trying to receive an Object from a EXTERN_CONSOLE_CONNECTION");

		Thread current = main.getLogLevel() >= 3 ? Thread.currentThread() : null;

		if(current != null) main.dbg(this.toString() + " started receiving in Thread \"" + current.getName() + "\"");

		try {

			synchronized (READING) {

				Object obj = in.readObject();
				if(current != null) main.dbg(this.toString() + " received \"" + obj + "\" in Thread \"" + current.getName() + "\"");
				try {

					return (T) obj;
				} catch(ClassCastException ex) {

					return null;
				}
			}
		} catch(EOFException ex) {

			if(current != null) main.dbg(this.toString() + " receiving ended with EOF-Ex in Thread \"" + current.getName() + "\"");
			return null;
		} catch(SocketException ex) {

			if(current != null) main.dbg(this.toString() + " receiving ended with Socket-Ex in Thread \"" + current.getName() + "\"");
			return null;
		} catch(Exception ex) {

			main.err(ex, "Exception in " + this.toString() + " whilst receiving in Thread \"" + (current == null ? Thread.currentThread().getName() : current.getName()) + "\"");
			return null;
		}
	}

	public String recvString() {

		Thread current = main.getLogLevel() >= 3 ? Thread.currentThread() : null;

		try {

			if(type != EXTERN_CONSOLE_CONNECTION) {

				Object obj = receive();
				if(obj == null) {

					return null;
				} else if(obj instanceof String) {

					return (String) obj;
				} else {

					main.err("Received a non String object, when expeting a String");
					return "";
				}
			} else {

				main.dbg("NON_UNICODE_CONSOLE_CONNECTION started receiving");
				synchronized(READING) {

					int length = in.readUnsignedShort();
					byte[] data = new byte[length];
					int read = in.read(data);

					if(read != length) {

						return null;
					}
					String ret = new String(data, "ISO-8859-1");
					if(current != null) main.dbg(this.toString() + " received \"" + ret + "\" in Thread \"" + current.getName() + "\"");
					return ret;
				}
			}
		} catch(Exception ex) {

			main.err(ex, "Exception in " + this.toString() + " whilst receiving in Thread \"" + (current == null ? Thread.currentThread().getName() : current.getName()) + "\"");
			return null;
		}
	}

	public boolean send(Serializable obj) {

		Thread current = main.getLogLevel() >= 3 ? Thread.currentThread() : null;

		if(current != null) main.dbg(this.toString() + " sending \"" + obj + "\" in Thread \"" + current.getName() + "\"");

		try {

			out.writeObject(obj);
			out.flush();
			return true;
		} catch(Exception ex) {

			return false;
		}
	}

	public boolean send(byte data) {

		Thread current = main.getLogLevel() >= 3 ? Thread.currentThread() : null;

		if(current != null) main.dbg(this.toString() + " sending Byte \"" + data + "\" in Thread \"" + current.getName() + "\"");

		try {

			out.writeByte(data);
			out.flush();
			return true;
		} catch(Exception ex) {

			return false;
		}
	}

	public void drop() {

		if(isClosed) return;

		Thread current = main.getLogLevel() >= 3 ? Thread.currentThread() : null;

		if(current != null) main.dbg(current.getName() + " dropping " + this.toString());

		isClosed = true;

		try {

			out.close();
			in.close();
			socket.close();
		} catch(Exception ignored) {}
	}

	public <T> Future<T> getAcknowledge() {

		return Executors.newCachedThreadPool().submit(new Callable<T>() {

			@Override
			@SuppressWarnings("unchecked")
			public T call() throws Exception {

				Object obj = receive();
				try {

					return (T) obj;
				} catch(ClassCastException ex) {

					throw new Exception(ex);
				}
			}
		});
	}

	public String toString() {

		return (type == SERVER_CONNECTION ? "Server-" : (type == CONTROLLER_CONNECTION ? "Controller-" :
			((type == INTERN_CONSOLE_CONNECTION || type == EXTERN_CONSOLE_CONNECTION) ? "Console-" : ""))) +
			"Connector" + (name.equals("UNSET") ? "" : " " + name ) + ": " + socket.getLocalAddress().toString() + "<>" + socket.getInetAddress().toString();
	}

	public boolean isClosed() {

		return isClosed;
	}

	public Socket getSocket() {

		return socket;
	}

	public InetAddress getLocalAddress() {

		return socket.getLocalAddress();
	}

	public InetAddress getRemoteAddress() {

		return socket.getInetAddress();
	}

	public int getType() {

		return type;
	}

	public void setType(int connectionType) {

		if(connectionType < 0 || connectionType > 4) throw new IllegalArgumentException("Connection type value is illegal");

		this.type = connectionType;
	}

	public String getName() {

		return name;
	}

	public void setName(String name) {

		this.name = name;
	}
}