package zservers.zsend.network;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import zservers.zsend.main.ZSend;
import zservers.zslib.network.Connector;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;

public class ControllerHandlerThread extends Thread {

	private final Object SENDING = new Object();

	private static ZSend main = ZSend.getInstance();

	private Connector connection;
	private Server server;

    private String name;

	private HashMap<ZCommand, ZAcknowledge> acknowledges;

	public ControllerHandlerThread(Connector connection, Server server, String name) {

		this.connection = connection;
		this.server = server;
		this.name = name;
		this.acknowledges = new HashMap<>();
        String address = connection.getSocket().getLocalAddress().toString();

		this.setName("ZSend \'" + name + "\' Handler Thread: " + address);
	}

	public void run() {

		try {

            thread:
			while(!isInterrupted()) {

				Object obj = connection.receive();
				if(obj == null) {

					break;
				} else if(obj instanceof String) {

					main.inf("Message from Controller \"" + name + "\": ", ((String) obj));
				} else if(obj instanceof ZCommand) {

					ZCommand cmd = (ZCommand) obj;

					cmd.setLocalConnection(connection);

					connection.send(main.dispatchCommand(cmd));
				} else if(obj instanceof ZAcknowledge) {

					ZAcknowledge ack = (ZAcknowledge) obj;

					for(ZCommand cmd : acknowledges.keySet())
						if(ack.isAcknowledgeFor(cmd)) {

							acknowledges.put(cmd, ack);
                            main.dbg("Received Acknowledge for command: " + cmd);
							continue thread;
						}

                    main.dbg("Received Acknowledge for command not waiting for");
				}
			}
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

	public void interrupt() {

		if(connection != null) connection.drop();
		super.interrupt();
	}

	public void sendCommand(ZCommand command) {

		if(main.getLogLevel() >= 3)
            main.dbg("Thread \"" + Thread.currentThread().getName() + "\"" + " sending command to controller \"" + name + "\"");
		synchronized (SENDING) {

			this.connection.send(command);
			acknowledges.put(command, null);
		}
	}

	public boolean hasAcknowledge(ZCommand command) {

		main.dbg("Checking for acknowledge in \"" + getName() + "\"");
		if(!acknowledges.containsKey(command))
			throw new IllegalArgumentException("Thread not waiting for an acknowledge of this command");

		return acknowledges.get(command) != null;
	}

    public ZAcknowledge getAcknowledge(ZCommand command) throws InterruptedException, TimeoutException {

        main.dbg("Getting acknowledge in \"" + getName() + "\"");

        int timeout = main.getConfig().getInt(ZSend.TIMEOUT_PATH);
        for(int loops = 0; !hasAcknowledge(command); loops++) {

            if(loops * 100 > timeout) throw new TimeoutException();
            Thread.sleep(100);
        }

        return acknowledges.get(command);
    }

	public void abortWaitForAcknowledge(ZCommand command) {

        main.dbg("No longer waiting for a acknowledge in \"" + getName() + "\"");
		if(!acknowledges.containsKey(command))
			throw new IllegalArgumentException("Thread not waiting for an acknowledge of this command");

		acknowledges.remove(command);
	}

	public boolean handles(Connector connection) {

		return this.connection.equals(connection);
	}

	public Connector getConnection() {

		return connection;
	}

	public String getControllerName() {

		return this.name;
	}
}
