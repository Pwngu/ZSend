package zservers.zsend.network;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import zservers.zsend.main.ZSend;
import zservers.zslib.network.*;
import zservers.zslib.network.Authenticator;

public class Server {

	private static ZSend main = ZSend.getInstance();

	private ServerSocket sSocket;

	private HashSet<ConnectThread> connectThreads;
	private ArrayList<ConsoleHandlerThread> consoleConnectionHandler;
	private HashMap<String, ServerHandlerThread> serverConnectionHandler;
	private HashMap<String, ControllerHandlerThread> controllerConnectionHandler;

	private ListenThread listenThread;

    private boolean isMaster;

	private volatile boolean isHosting;

	public Server() {

		connectThreads = new HashSet<>();
		controllerConnectionHandler = new HashMap<>();
		consoleConnectionHandler = new ArrayList<>();
		serverConnectionHandler = new HashMap<>();

        isMaster = main.getConfig().getString(ZSend.MASTER_IP_PATH) == null;
	}

	public void startHosting(int port) {

		if(isHosting) {

			main.wrn("Can't start to host, already hosting");
			return;
		}

		try {

			sSocket = new ServerSocket(port);

			main.inf("Started hosting on port: " +  main.getConfig().getInt(ZSend.PORT_PATH));
			listenThread = new ListenThread();
			isHosting = true;
			main.startThread(listenThread);
		} catch(IOException ex) {

			main.err("Couldn't bind to port " +  port);
		} catch(Exception ex) {

			main.err(ex, "Exception whilst instantiating server");
		}

        if(!isMaster) connectToMaster();
	}

    public void connectToMaster() {

        main.startThread(new MasterConnectThread());
    }

	public void stopHosting() {

		if(!isHosting) {

			main.wrn("Can't stop to host, currently not hosting");
			return;
		}

		isHosting = false;
		main.inf("Stopped hosting");
		listenThread.interrupt();
	}

	public boolean isHosting() {

		return isHosting;
	}

	public void connect(Socket socket) {

		ConnectThread thread = new ConnectThread(this, socket);
		if(main.startThread(thread)) connectThreads.add(thread);
	}

	public void disconnect(Connector connection) {

        if(connection.getType() == Connector.INTERN_CONSOLE_CONNECTION || connection.getType() == Connector.EXTERN_CONSOLE_CONNECTION) {

            for(ConsoleHandlerThread thread : consoleConnectionHandler) {

                if(thread.handles(connection)) {

                    connection.drop();

                    thread.interrupt();
                    main.inf("Console " + consoleConnectionHandler.indexOf(thread) + " connection closed: " + connection.toString());
                    consoleConnectionHandler.remove(thread);
                    main.dbg(thread.getName() + " removed from console connection handlers");

                    return;
                }
            }
        } else if(connection.getType() == Connector.SERVER_CONNECTION) {

            for(String name : serverConnectionHandler.keySet()) {

                ServerHandlerThread thread = serverConnectionHandler.get(name);
                if(thread.handles(connection)) {

                    connection.drop();

                    thread.interrupt();
                    main.inf("Server " + thread.getServerName() + " connection closed: " + connection.toString());
                    serverConnectionHandler.remove(name);
                    main.dbg(thread.getName() + " removed from server connection handlers");

                    return;
                }
            }
        } else if(connection.getType() == Connector.CONTROLLER_CONNECTION) {

            for(String name : controllerConnectionHandler.keySet()) {

                ControllerHandlerThread thread = controllerConnectionHandler.get(name);
                if(thread.handles(connection)) {

                    connection.drop();

                    thread.interrupt();
                    main.inf("Controller " + thread.getControllerName() + " connection closed: " + connection.getSocket().getLocalAddress().toString());
                    controllerConnectionHandler.remove(name);
                    main.dbg(thread.getName() + " removed from controller connection handlers");

                    return;
                }
            }
		}
	}

	public void connected(ConnectThread thread) {

		connectThreads.remove(thread);
	}

	public void disconnectAll(boolean preventReconnect) {

        main.dbg("Disconnecting all" + (preventReconnect ? ", preventing reconnect" : ""));
        if(!consoleConnectionHandler.isEmpty()) {

            for(ConsoleHandlerThread thread : consoleConnectionHandler) {

                thread.interrupt();
            }
        }
        if(!serverConnectionHandler.isEmpty()) {

            for(String name : serverConnectionHandler.keySet()) {

                if(preventReconnect) serverConnectionHandler.get(name).sendCommand(new ZCommand(null, serverConnectionHandler.get(name).getServerName(), null, "zr disconnect"));
                serverConnectionHandler.get(name).interrupt();
            }
        }
        if(!controllerConnectionHandler.isEmpty()) {

            for(String name : controllerConnectionHandler.keySet()) {

                if(preventReconnect && isMaster) controllerConnectionHandler.get(name).sendCommand(new ZCommand(controllerConnectionHandler.get(name).getControllerName(), null, null, "zs drop controller " + main.getServerName()));
                controllerConnectionHandler.get(name).interrupt();
            }
        }
        if(!connectThreads.isEmpty()) {

            for(ConnectThread thread : connectThreads) {

                thread.interrupt();
            }
        }
	}

	public HashMap<String, ServerHandlerThread> getServerHandlerThreads() {

		return serverConnectionHandler;
	}

	public ArrayList<ConsoleHandlerThread> getConsoleHandlerThreads() {

		return consoleConnectionHandler;
	}

	public HashMap<String, ControllerHandlerThread> getControllerHandlerThreads() {

		return controllerConnectionHandler;
	}

	public HashSet<ConnectThread> getConnectThreads() {

		return connectThreads;
	}

    public boolean isMaster() {

        return isMaster;
    }

	private class ListenThread extends Thread {

		public ListenThread() {

			this.setName("ZSend Listen Thread");
		}

		public void run() {

			try {

				while(!isInterrupted()) {

					Socket client = sSocket.accept();

					connect(client);
				}
				main.endThread(this);
			} catch(SocketException ex) {

				main.endThread(this);
			} catch(Exception ex) {

				main.err(ex, "Exception in " + this.getName());
				main.endThread(this);
			}
		}

		@Override
		public void interrupt() {

			try{sSocket.close();}catch(Exception ignored){}
			super.interrupt();
		}
	}

    private class MasterConnectThread extends Thread {

        public MasterConnectThread() {

            this.setName("ZSend master server connect thread");
        }

        @Override
        public void run() {

            main.dbg("Connecting to master server");

            String ipport = main.getConfig().getString(ZSend.MASTER_IP_PATH);
            String mIP;
            int mPort;

            try {
                if (!ipport.contains(":")) {

                    mIP = ipport;
                    mPort = main.getConfig().getInt(ZSend.PORT_PATH);
                } else {

                    mIP = ipport.substring(0, ipport.indexOf(':'));
                    mPort = Integer.parseInt(ipport.substring(ipport.indexOf(':') + 1, ipport.length()));
                }
            } catch (NumberFormatException ex) {

                main.err("Couldn't connect to master server, illegal ip configuration");
                return;
            }

            Connector connection = null;

            int loops = 0;
            do {

                try {

                    loops++;
                    if(loops != 1) {

                        Thread.sleep(5000);
                    }

                    main.dbg("Starting to connect");
                    Authenticator auth = new Authenticator(new ServerInfo(main.getConfig().getString(ZSend.SERVER_ID_PATH),
                            ServerInfo.CONTROLLER), Connector.CONTROLLER_CONNECTION);
                    connection = new Connector(new Socket(mIP, mPort), "Connection Handler");
                    connection.send(auth);

                    ZAcknowledge acknowledge = connection.receive();

                    if(acknowledge == null) {

                        main.err("Didn't get correct acknowledge, retrying in 5 seconds...");
                        continue;
                    }

                    if(!acknowledge.isAcknowledgeFor(auth)) {

                        main.err("Dropping connection");
                        connection.drop();
                        return;
                    } else if(acknowledge.state == ZAcknowledge.SUCCESS) {

                        //TODO find out the server's name
                        ControllerHandlerThread thread = new ControllerHandlerThread(connection, Server.this, "ZSMaster");

                        if(main.startThread(thread)) {

                            getControllerHandlerThreads().put("ZSMaster", thread);
                            main.inf("Successfully connected to master Server");
                            return;
                        }
                    } else {

                        main.err("Error whilst authenticating");
                        main.err(acknowledge.state + " >> " + acknowledge.message);
                        connection.drop();
                        return;
                    }

                    if((loops == 1 || loops % 12 == 0) && connection.isClosed()) main.err("Couldn't connect to master server");

                } catch(UnknownHostException ex) {

                    main.dbg("Couldn't find host whilst connecting, retrying in 5 seconds...");

                } catch (ConnectException ex) {

                    main.dbg("Couldn't connect to master server, retrying in 5 seconds...");
                } catch(Exception ex) {

                    main.dbg(ex, "Error whilst connecting to master server, retrying in 5 seconds...");
                }
            } while((connection == null || connection.isClosed()) && !isInterrupted());
        }
    }
}
