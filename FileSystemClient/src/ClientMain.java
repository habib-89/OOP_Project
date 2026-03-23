import java.io.*;
import java.net.Socket;

public class ClientMain {

    public static void main(String[] args) {

        try {
            Socket socket = new Socket("localhost", 5001);

            BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in));

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            PrintWriter out = new PrintWriter(
                    socket.getOutputStream(), true);

            System.out.println("Connected to server.");
            out.println("LOGIN admin 1234");
            System.out.println(in.readLine());


            String userInput;

            while (true) {
                userInput = console.readLine();
                out.println(userInput);

                String response = in.readLine();
                System.out.println(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
