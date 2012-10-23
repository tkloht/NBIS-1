/*
 */
package exercise1;

import java.io.IOException;

/**
 *
 * @author sven
 */
public class Exercise1 {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
	System.out.println("Starting web server ...");
	
	WebServer server = new WebServer(null);
	server.start(8080);
    }
}
