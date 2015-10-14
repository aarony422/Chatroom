import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Chatroom {
	
	// current participants in Chatroom
	// Socket maps to their name
	HashMap<Socket, Client> participants;
	
	// port number
	int port;
	
	// ServerSocket
	ServerSocket server_socket;

	// chatroom constructor
	public Chatroom(int port) {
		// use default port 8080
		if (port == -1) {
			this.port = 8080;
		} else {
			this.port = port;
		}

		ServerSocket server_sock = null;
		try {
			server_sock = new ServerSocket(this.port);
			server_sock.setReuseAddress(true);
		} catch (IOException e) {
			System.err.println("Creating socket failed");
			System.exit(1);
		} catch (IllegalArgumentException e) {
			System.err.println("Error binding to port");
			System.exit(1);
		}
		this.server_socket = server_sock;
		
		this.participants = new HashMap<Socket, Client>();
	}
	
	// listens for client connections on port and calls handler
	public void openChatroom() throws IOException {
		try {
			// listen for client connections
			while (true) {
				try {
					Socket sock = this.server_socket.accept();
					chatHandler ch = new chatHandler(sock);
					Thread t = new Thread(ch);
					t.start();
				} catch (IOException e) {
					System.err.println("Error accpting connection");
					continue;
				}
			}
		} finally {
			this.server_socket.close();
		}
	}
	
	// sends greeting message and gets client name
	// listen for message from clietn socket and broadcasts
	// the messages
	public void handle_client(Socket sock) throws IOException {
		// establish input and output streams
		InputStream instream = null;
		OutputStream outstream = null;
		
		try {
			instream = sock.getInputStream();
			outstream = sock.getOutputStream();
		} catch (IOException e) {
			System.err.println("Error: fail to establish streams");
			sock.close();
			return;
		}
		
		// send greeting message, and get client name
		sendGreetings(outstream);
		
		// listen for client name
		String client_name;;
		client_name = getName(instream);
		Client c = new Client(sock, client_name);
		
		// notify chatroom about new client
		notifyChatNewClient(client_name, sock);
		
		// add to participants
		addParticipant(sock, c);
		
		// notify current client of all current participants
		currentParticipants(outstream, client_name);
		
		// listen to client
		int len = 0;
		byte[] data = new byte[2000];
		StringBuffer content = null;
		while ((len = instream.read(data)) != -1) {
			content = new StringBuffer();
			String s = new String(data, 0, len);
			content.append(s);
			if (content.toString().contains("\n")) {
				String message = getLine(content.toString());
				//System.out.println(message);
				sendToAll(message, sock);
			}
		}
		
		// remove from participants
		removeParticipant(sock);
		sock.close();
	}
	
	// notifies current sock of the existing
	public void currentParticipants(OutputStream outStream, String client_name) {
		String names = getNames(client_name);
		String message;
		if (names.length() != 0) {
			message = "*****Participants currently online:*****\n" + names;
		} else {
			message = "*****You are the only one online!*****\n";
		}
		
		//System.out.println(message);
		sendMsg(message, outStream);
	}
	
	// gets all names of current participants
	public synchronized String getNames(String client_name) {
		StringBuilder rtn = new StringBuilder();
		String name;
		Iterator it = this.participants.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Socket, Chatroom.Client> pair = (Map.Entry<Socket, Chatroom.Client>)it.next();
			name = pair.getValue().Name;
			rtn.append("     * ");
			rtn.append(name);
			if (name.equals(client_name)) {
				rtn.append(" (you)");
			}
			rtn.append("\n");
		}
		return rtn.toString();
	}
	
	// sends all participants the message, with the
	// client_name: message format
	public synchronized void sendToAll (String message, Socket sock) {
		String client_name = this.participants.get(sock).Name;
		//System.out.println("client name is : " + client_name);
		message = client_name + ": " + message + "\n";
		broadcastMsg(message, sock);
	}
	
	// removes the participant on socket sock
	// broadcasts the message that client has left the Chatroom
	public synchronized void removeParticipant(Socket sock) {
		String client = this.participants.get(sock).Name;
		this.participants.remove(sock);
		String message = "*****" + client + " has left the Chatroom!*****\n";
		broadcastMsg(message, sock);
	}
	
	// Sends message to all current participants
	// calls sendMsg
	public synchronized void broadcastMsg (String message, Socket sock) {
		Iterator it = this.participants.entrySet().iterator();
		OutputStream outstream = null;
		while (it.hasNext()) {
			Map.Entry<Socket, Chatroom.Client> pair = (Map.Entry<Socket, Chatroom.Client>)it.next();
			try {
				outstream = pair.getValue().socket.getOutputStream();
			} catch (IOException e) {
				System.err.println("Error: Client stream has closed!");
			}
			if (pair.getKey() != sock) {
				sendMsg(message, outstream);
			}
		}
	}
	
	// method that notifies all current participants that client has joined Chatroom
	// method calls broadcastMsg method
	public synchronized void notifyChatNewClient(String name, Socket sock) throws IOException {
		if (isChatroomEmpty()) {
			return;
		}
		String message = "*****" + name + " has joined the Chatroom!*****\n";		
		broadcastMsg(message, sock);
	}
	
	// client with socket sock is added to participants
	public synchronized void addParticipant(Socket sock, Client c) {
		this.participants.put(sock, c);
	}
	
	// Returns the next line with the newline character removed
	public String getLine(String line) {
		String[] rtn = line.split("\n");
		return rtn[0].substring(0, rtn[0].length() - 1);
	}
	
	// sends the greetings message to outstream
	// calls sendMsg
	public void sendGreetings(OutputStream outstream) {
		String greetings = "*****Welcome to the Chatroom!*****\nPlease enter your name: ";
		sendMsg(greetings, outstream);
	}
	
	// Gets the first line the client inputs
	// Assumes it to be the user's name
	public String getName(InputStream instream) throws IOException{
		int len = 0;
		byte[] data = new byte[2000];
		StringBuffer content = null;
		try {
			while ((len = instream.read(data)) != -1) {
				content = new StringBuffer();
				String s = new String(data, 0, len);
				content.append(s);
				if (content.toString().contains("\n")) {
					break;
				}
			}
		} catch (NullPointerException e) {
			System.err.println("Error User left without entering name!");
		}
		
		String rtn[] = null;
		
		try {
			rtn = content.toString().split("\n");
		} catch (NullPointerException e) {
			System.err.println("Error: User left without entering name!");
			
		}
		return rtn[0].substring(0, rtn[0].length()-1);
	}
	
	// sends message to a particular outstream
	public void sendMsg (String message, OutputStream outstream) {
		try {
			outstream.write(message.getBytes());
		} catch (IOException e) {
			System.err.println("Error: Sending Fails");
			return;
		} catch (NullPointerException e) {
			return;
		}
	}
	
	// returns true if chatroom is empty
	public boolean isChatroomEmpty() {
		return this.participants.isEmpty();
	}


	public static void main(String[] args) throws IOException {
		if (args.length > 1) {
			System.err.println("Usage: Chatroom <optional port>");
			System.exit(-1);
		}
		
		Chatroom myChatroom;
		int port = -1;

		// if port number specified
		if (args.length == 1) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Usage: <optional port>. Invalid port number.");
				System.exit(-1);
			}
			myChatroom = new Chatroom(port);
		}
		
		// no port number specified
		myChatroom = new Chatroom(port);
		
		// open myChatroom
		myChatroom.openChatroom();
	}
	
	// object to store client name and socket information
	public class Client {
		Socket socket;
		String Name;
		
		public Client(Socket socket, String name) {
			this.socket = socket;
			this.Name = name;
		}
	}
	
	// runnable object that will handle multithreaded processes
	public class chatHandler implements Runnable {
		Socket socket;

		public chatHandler(Socket socket) {
			this.socket = socket;
		}

		// override run()
		@Override
		public void run() {
			// respond to the client
			try {
				handle_client(socket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
	

}
