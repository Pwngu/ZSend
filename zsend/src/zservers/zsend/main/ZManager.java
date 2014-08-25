package zservers.zsend.main;

import static zservers.zlib.utilities.Utilities.joinString;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import zservers.zlib.utilities.ConsoleReader;
import zservers.zlib.utilities.ReaderThread;
import zservers.zlib.utilities.Utilities;
import zservers.zslib.network.Authenticator;
import zservers.zslib.network.Connector;
import zservers.zslib.network.ZAcknowledge;
import zservers.zslib.network.ZCommand;

public class ZManager implements ConsoleReader {

	private String prompt;

	private String connectedServer;

	private String executer;
	private String bungee;
	private String server;

	private ReaderThread thread;

	private String ip;
	private int port;
	private Connector connection;

	private boolean pw;

	public static void main(String[] args) {

		if(args.length != 2) {

			System.out.println("Illegal number of arguments");
			return;
		}

		ZManager manager;

		String ip = args[0];
		int port;
		try {

			port = Integer.parseInt(args[1]);
			manager = new ZManager(ip, port);
		} catch(NumberFormatException ex) {

			System.out.println("Illegal argument, port is not a number");
			return;
		} catch (IOException ex) {

			System.out.println("Couldn't connect to server: " + ex.toString());
			return;
		}
        while(!manager.pw)
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            manager.startReceiving();
	}

	public ZManager(String ip, int port) throws UnknownHostException, IOException {

		pw = false;

        prompt = "Password: ";

        this.ip = ip;
        this.port = port;

		connection = new Connector(new Socket(ip, port));

		thread = new ReaderThread(this);
		thread.start();
		synchronized(ReaderThread.LOCK) {ReaderThread.LOCK.notifyAll();}
	}

	public void startReceiving() {

		Thread current = Thread.currentThread();
		while(!current.isInterrupted()) {

			Object obj = connection.receive();

            if(obj == null) {

                connection.drop();
                return;
            }

			System.out.println(obj);

            synchronized(ReaderThread.LOCK) {ReaderThread.LOCK.notifyAll();}
		}
	}

	@Override
	public boolean command(String command, String[] args) {

		if(pw) {

			switch(command.toLowerCase()) {

			case "close":
				if(args.length != 0) {

					dispatchServerCommand(command, args);
					return false;
				}

				connection.drop();

				this.thread.interrupt();
				return false;

			case "uses":
			case "use-server":
				if(args.length > 1) {

					dispatchServerCommand(command, args);
					return true;
				} else if(args.length == 0) {

					this.server = null;
					newPrompt();
					return false;
				}
				else this.server = args[0];

				newPrompt();

				System.out.println("Server: " + args[0]);
				return false;

			case "useb":
			case "use-bungee":
				if(args.length > 1) {

					dispatchServerCommand(command, args);
					return true;
				} else if(args.length == 0) {

					this.bungee = connectedServer;
					newPrompt();
					return false;
				}

				this.bungee = args[0];

				newPrompt();

				System.out.println("Bungee: " + args[0]);

				return false;

			case "exec":
			case "executor":
				if(args.length > 1) {

					dispatchServerCommand(command, args);
					return true;
				} else if(args.length == 0) {

					this.executer = null;
					newPrompt();
					return false;
				}

				this.executer = args[0];

				newPrompt();

				System.out.println("Executor: " + args[0]);

				return false;

//			case "rep":
//			case "repeat":
//				dispatchServerCommand(null, null);
//				return;

			default:
				dispatchServerCommand(command, args);
				return true;
			}
		} else {

            connection.send(new Authenticator(command, null, Connector.INTERN_CONSOLE_CONNECTION));

			do {

				try {

					ZAcknowledge ack = connection.receive();

					if(ack == null) {

						System.out.println("Error whilst connecting, reconnecting...");
					} else if(ack.state == ZAcknowledge.SUCCESS) {

						System.out.println("Successfully connected to Server");
                        connectedServer = Utilities.substringBetween(ack.info, '\"', 0);
                        newPrompt();
                        pw = true;
						break;
					} else {

						System.out.println("Error whilst connecting, aborting!");
						throw new RuntimeException("Connecting interrupted");
					}

					Thread.sleep(1000);

				} catch(InterruptedException ex) {

					throw new RuntimeException("Connecting interrupted");
				} catch(Exception ex) {

					throw new RuntimeException("Connecting interrupted", ex);
				}

				try {

					connection = new Connector(new Socket(ip, port), Connector.INTERN_CONSOLE_CONNECTION, "ZSend ZManager Connection");
					connection.send(new Authenticator(command, null, Connector.INTERN_CONSOLE_CONNECTION));
				} catch (UnknownHostException ex) {

					throw new RuntimeException("Host has been disconnected");
				} catch (Exception ex) {

					throw new RuntimeException("Exception whilst connecting", ex);
				}
			} while(true);
			return false;
		}
	}

	private void dispatchServerCommand(String arg, String[] args) {

        ZCommand command = new ZCommand(bungee, server, executer, arg + " " + joinString(args, " "));

//		String line = arg + (args == null ? "" : " " + joinString(args, " "));
//        String command = (bungee != null ? "{" + bungee + "}" : "{" + connectedServer + "}") +
//                (server != null ? "[" + server + "]" : "") + (executer != null ? "(" + executer + ")" : "") + ":" + line;


		System.out.println("Sending: " + command);
		if(!connection.send(command))
			System.err.println("Error while sending command!");
	}

	private void newPrompt() {

		prompt = (executer != null ? executer + "@" : "") + (bungee != null ? bungee : connectedServer) +
                (server != null ? ("|" + server) : "") + "> ";
	}

	@Override
	public String getPrompt() {

		return prompt;
	}
}
