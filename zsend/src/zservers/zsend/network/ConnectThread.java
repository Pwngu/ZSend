package zservers.zsend.network;

import java.net.Socket;
import java.util.List;

import net.md_5.bungee.api.ProxyServer;
import zservers.zsend.main.ZSend;
import zservers.zslib.network.Authenticator;
import zservers.zslib.network.Connector;
import zservers.zslib.network.Id;
import zservers.zslib.network.ZAcknowledge;
import zservers.zlib.utilities.Utilities;

public class ConnectThread extends Thread {

	private static ZSend main = ZSend.getInstance();

	private Server server;
	private Socket socket;

	private Connector connection;

	public ConnectThread(Server server, Socket socket) {

		this.server = server;
		this.socket = socket;

		setName("ZSend Connect Thread " + socket.getLocalAddress().toString());
	}

	public void run() {

        try {

            connection = new Connector(socket);
            main.inf(socket.getLocalAddress().toString() + " trying to connect...");

            Object obj = connection.receive();

            String serverName;

            if(obj instanceof Authenticator) {

                main.dbg("Connecting with Authenticator");

                Authenticator auth = (Authenticator) obj;

                if(!auth.isLegal()) {

                    main.wrn("Illegal Authenticator, dropping connection");
                    connection.drop();
                    main.endThread(this);
                    return;
                }

                if(auth.connectionType != Connector.CONTROLLER_CONNECTION &&
                    auth.connectionType != Connector.INTERN_CONSOLE_CONNECTION &&
                    auth.connectionType != Connector.SERVER_CONNECTION) {

                        main.wrn("Illegal connection Type in Authenticator, dropping connection");
                        connection.drop();
                        main.endThread(this);
                        return;
                }

                connection.setType(auth.connectionType);
                serverName = auth.serverInfo.name;

            } else if(obj instanceof String) {

                main.dbg("Connecting with String authentication");

                connection.setType(Connector.EXTERN_CONSOLE_CONNECTION);

                serverName = null;

            } else {

                main.wrn("Received " + obj + ", Closing connection...");
                connection.drop();
                main.endThread(this);
                return;
            }

            String ip = connection.getSocket().getInetAddress().getHostAddress();

            if(connection.getType() == Connector.SERVER_CONNECTION) {

                main.dbg("Connecting in Channel: SERVER",
                         "With name: " + serverName);

                if(!main.getConfig().getStringList(ZSend.SERVER_IPS_PATH).contains(ip)) {

                    main.wrn("Server authentication failed, wrong ip: " + ip);
                    sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                if(server.getServerHandlerThreads().containsKey(serverName)) {

                    main.wrn("Server authentication failed, there's already a server connected with this name");
                    sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                if(!ProxyServer.getInstance().getServers().containsKey(serverName)) {

                    main.wrn("Server authentication failed, invalid name");
                    sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                main.inf("Server authentication successful >> \"" + serverName + "\"");
                sendAcknowledge(obj, "Authentication successful", ZAcknowledge.SUCCESS, connection.getType());

                ServerHandlerThread thread = new ServerHandlerThread(connection, server, serverName);
                if(main.startThread(thread)) server.getServerHandlerThreads().put(serverName, thread);

            } else if(connection.getType() == Connector.CONTROLLER_CONNECTION) {

                main.dbg("Connecting in Channel: CONTROLLER",
                         "With name: " + serverName);

                if(!server.isMaster()) {

                    main.wrn("Controller trying to connect, but this server isn't a master");
                    sendAcknowledge(obj, "This server isn't a ZSend master", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                if (!main.getConfig().getStringList(ZSend.CONTROLLER_IPS_PATH).contains(ip)) {

                    main.wrn("Controller authentication failed, wrong ip");
                    sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                if(server.getControllerHandlerThreads().containsKey(serverName)) {

                    main.wrn("Controller authentication failed, there's already a controller connected with this name");
                    sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                    connection.drop();
                    main.endThread(this);
                    return;
                }

                main.inf("Controller authentication successful >> \"" + serverName + "\"");
                sendAcknowledge(obj, "Authentication successful", ZAcknowledge.SUCCESS, connection.getType());

                ControllerHandlerThread thread = new ControllerHandlerThread(connection, server, serverName);
                if(main.startThread(thread)) server.getControllerHandlerThreads().put(serverName, thread);

            } else if(connection.getType() == Connector.INTERN_CONSOLE_CONNECTION) {

                main.dbg("Connecting in Channel: INT-CONSOLE");

                List<String> list = main.getConfig().getStringList(ZSend.CONSOLE_IPS_PATH);

                if (!list.isEmpty()) {

                    if (!list.contains(ip)) {

                        main.wrn("Console authentication failed, wrong ip");
                        sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                        connection.drop();
                        main.endThread(this);
                        return;
                    }
                } else if(!((Authenticator) obj).checkPassword(Utilities.md5(main.getConfig().getString(ZSend.PW_PATH)))) {

                    main.wrn("Illegal Authenticator, illegal Password, dropping connection");
                    connection.drop();
                    main.endThread(this);
                    return;
                }

                main.inf("Intern-Console connection accepted: " + connection.getSocket().getLocalAddress().toString());
                sendAcknowledge(obj, "Authentication successful", ZAcknowledge.SUCCESS, connection.getType());
                ConsoleHandlerThread handler = new ConsoleHandlerThread(connection, this.server);

                if(main.startThread(handler)) server.getConsoleHandlerThreads().add(handler);
            } else if(connection.getType() == Connector.EXTERN_CONSOLE_CONNECTION) {

                main.dbg("Connecting in Channel: EXT-CONSOLE");

                List<String> list = main.getConfig().getStringList(ZSend.CONSOLE_IPS_PATH);

                if (!list.isEmpty()) {

                    if (!list.contains(ip)) {

                        main.wrn("Console authentication failed, wrong ip");
                        sendAcknowledge(obj, "Authentication failed", ZAcknowledge.ERROR, connection.getType());

                        connection.drop();
                        main.endThread(this);
                        return;
                    }
                } else if(!((String) obj).equalsIgnoreCase(Utilities.md5(main.getConfig().getString(ZSend.PW_PATH)))) {

                    main.wrn("Illegal Authenticator, illegal Password, dropping connection");
                    connection.drop();
                    main.endThread(this);
                    return;
                }

                main.inf("Extern-Console connection accepted: " + connection.getSocket().getLocalAddress().toString());
                ConsoleHandlerThread handler = new ConsoleHandlerThread(connection, this.server);

                if(main.startThread(handler)) server.getConsoleHandlerThreads().add(handler);
            }
        } catch (Exception ex) {

            main.err(ex, "Exception whilst connecting",
                    "Closing connection...");

            if(connection != null) connection.drop();
        } catch (ThreadDeath death) {

            main.err("Closing connection...");
            if(connection != null) connection.drop();
            throw death;
        }
        main.endThread(this);
    }

	public void sendAcknowledge(Object obj, String message, int state, int contype) {

        assert obj instanceof Id;

		if(contype != Connector.EXTERN_CONSOLE_CONNECTION) {

			connection.send(new ZAcknowledge((Id) obj, "\"" + main.getServerName() + "\" authentication acknowledge",
					state, message));
		} else {

			connection.send(state + ":" + message);
		}
	}

	public void interrupt() {

		if(connection != null) connection.drop();
		super.interrupt();
	}

	public Connector getConnection() {

		return connection;
	}
}
