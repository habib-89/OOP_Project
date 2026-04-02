import java.io.*;
import java.net.Socket;

/**
 * Simple CLI test client for the FileVault server.
 * Uses DataInputStream/DataOutputStream to match the server's protocol.
 */
public class ClientMain {

    public static void main(String[] args) {

        try (Socket socket = new Socket("localhost", 5001)) {

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Connected to server.");
            System.out.print("Username: ");
            String username = console.readLine();
            System.out.print("Password: ");
            String password = console.readLine();

            out.writeUTF("LOGIN " + username + " " + password);
            String loginResponse = in.readUTF();
            System.out.println("Login: " + loginResponse);

            if (!loginResponse.equals("SUCCESS")) {
                System.out.println("Login failed. Exiting.");
                return;
            }

            System.out.println("Type commands (e.g. LISTDIR, MKDIR foldername, LOGOUT to quit):");

            String userInput;
            while ((userInput = console.readLine()) != null) {
                if (userInput.equalsIgnoreCase("LOGOUT") || userInput.equalsIgnoreCase("EXIT")) {
                    System.out.println("Disconnecting.");
                    break;
                }

                if (userInput.trim().isEmpty()) continue;

                out.writeUTF(userInput);
                String response = in.readUTF();
                System.out.println("Server: " + response);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}