/*
 */
package exercise1;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Liefert HTML-Dokumente im Verzeichnis htdocs per HTTP aus, bei Fehlern wird
 * die Datei error/HTTPCODE.html geliefert. Wird das angeforderte Dokument bsp.
 * nicht gefunden, so wird error/404.html geliefert.
 * @author sven
 */
public class WebServer {

    private ServerSocket _serverSocket;
    private UriConverter _uriConverter;

    public WebServer(UriConverter uriConverter) throws IOException {
	_uriConverter = uriConverter;
    }

    /**
     * Liest den HTTP-Request in eine Datenstruktur ein.
     * @param requestStream
     * @return
     * @throws HttpError
     * @throws IOException 
     */
    private HttpRequest readRequest(BufferedReader requestStream) throws HttpError, IOException {
	HttpRequest request = new HttpRequest();

	/*
	 * Read method and resource.
	 */
	String requestLine = requestStream.readLine();
	String[] parts = requestLine.split(" ");

	if (parts.length != 3) {
	    throw new HttpError(Http.Status.BadRequest);
	}

	request.method = parts[0].trim().toLowerCase();
	request.resource = parts[1].trim();
	request.version = parts[2].trim().toLowerCase();
	
	/*
	 * Extract get parameters.
	 */
	if (request.resource.indexOf("?") != -1) {
	    request.queryString = request.resource.substring(request.resource.indexOf("?") + 1);
	    request.resource = request.resource.substring(0, request.resource.indexOf("?"));
	} else {
	    request.queryString = "";
	}


	/*
	 * Read header fields.
	 */
	requestLine = requestStream.readLine().trim();
	while (requestLine.length() > 0) {
	    int split = requestLine.indexOf(":");

	    if (split == -1) {
		System.err.println("Unknown request format: '" + requestLine + "'");
		throw new HttpError(Http.Status.BadRequest);
	    }

	    request.header.put(requestLine.substring(0, split).trim().toLowerCase(), requestLine.substring(split + 1).trim());
	    requestLine = requestStream.readLine().trim();
	}

	return request;
    }

    private HttpResponse handle(HttpRequest request) throws HttpError {
	HttpResponse response = new HttpResponse();

	/*
	 * Bad hack!
	 */
	if (request.queryString.equals("header=show")) {
	    response.setBody(request.toString());
	} else {
	    /*
	     * Use index.html for the root directory.
	     */
	    if (request.resource.equals("/")) {
		request.resource = "/index.html";
	    }
	    
	    try {
		response.setBody(new File("htdocs" + request.resource));
		response.status = Http.Status.Ok;
	    } catch (FileNotFoundException err) {
		throw new HttpError(Http.Status.NotFound);
	    } catch (IOException err) {
		throw new HttpError(Http.Status.InternalServerError);
	    }
	}

	return response;
    }

    /**
     * Loop der Anfragen entgegen nimmt.
     * @param portNumber
     * @throws IOException 
     */
    public void start(int portNumber) throws IOException {
	_serverSocket = new ServerSocket(portNumber);
	Socket client;
	HttpResponse response = null;

	System.out.println("Waiting for new connections on port " + portNumber + ".");

	while ((client = _serverSocket.accept()) != null) {
	    System.out.println("New connection from " + client.getRemoteSocketAddress() + ".");

	    try {
		HttpRequest request = readRequest(
			new BufferedReader(
			new InputStreamReader(
			client.getInputStream())));

		/*
		 * Sanity checks.
		 */
		if (request.resource.indexOf("..") != -1) {
		    throw new HttpError(Http.Status.Forbidden);
		}

		System.out.println(request);
		response = handle(request);


	    } catch (HttpError err) {
		System.err.println("Http error " + err.getStatus().code() + " raised.");
		response = new HttpResponse();

		try {
		    response.status = Http.Status.NotFound;
		    response.setBody(new File("error/" + err.getStatus().code() + ".html"));
		} catch (FileNotFoundException e) {
		    response.status = Http.Status.InternalServerError;
		    response.setBody("Could not load custom " + err.getStatus().code() + " error page!");
		}

	    } finally {
		System.out.println("-> " + response.status.code());
		response.serialize(client.getOutputStream());
		client.close();
	    }
	}
    }
}

class HttpRequest {

    public Map<String, String> header;
    public String method;
    public String resource;
    public String version;
    public String queryString;

    public HttpRequest() {
	header = new HashMap<String, String>();
    }

    @Override
    public String toString() {
	StringBuilder sb = new StringBuilder(method + " " + resource + ((queryString.length() > 0) ? "?" + queryString + " " : " ") + version + "\n");

	for (String key : header.keySet()) {
	    sb.append(key);
	    sb.append(": ");
	    sb.append(header.get(key));
	    sb.append("\n");
	}
	sb.deleteCharAt(sb.length() - 1);
	return sb.toString();
    }
}

class Http {

    public static final HashMap<String, String> MIME_TYPES;
    public enum Status {
	Ok(200, "OK"),
	BadRequest(400, "Bad Request"),
	Forbidden(403, "Forbidden"),
	NotFound(404, "Not Found"),
	InternalServerError(500, "Internal Server Error");
	
	private final int _code;
	private final String _reason;
	
	Status(int code, String reason) {
	    _code = code;
	    _reason = reason;
	}
	
	public int code() {
	    return _code;
	}
	
	public String reason() {
	    return _reason;
	}
    };
    
    static{
	MIME_TYPES = new HashMap<String, String>();
	
	MIME_TYPES.put("html", "text/html");
	MIME_TYPES.put("txt", "text/plain");
	MIME_TYPES.put("png", "image/png");
	MIME_TYPES.put("jpg", "image/jpeg");
    }
    
    /**
     * Versucht durch den Namen auf den Typ zu schließen.
     * @param name
     * @return 
     */
    public static String getMimeTypeByFileName(String name) {
	int nameExtensionSplit = name.lastIndexOf(".");

	
	if (nameExtensionSplit != -1) {
	    String ext = name.substring(nameExtensionSplit + 1);
	    if (Http.MIME_TYPES.containsKey(ext)) {
		return Http.MIME_TYPES.get(ext);
	    }
	}
	
	return "application/octet-stream";
    }
}

class HttpResponse {

    public InputStream body;
    public long bodyLength = -1;
    public Http.Status status = Http.Status.Ok;
    public String mimeType = "text/html";
    public String mediaType = Charset.defaultCharset().displayName();

    private void print(PrintStream s, String str) {
	s.print(str);
	s.write('\r');
	s.write('\n');
    }

    public void setBody(String s) {
	body = new ByteArrayInputStream(s.getBytes());
	bodyLength = s.length();
	mimeType = "text/plain";
    }

    public void setBody(InputStream s, long length) {
	if (length >= -1) {
	    body = s;
	    bodyLength = length;
	    mimeType = "application/octet-stream";
	}
    }

    public void setBody(File f) throws FileNotFoundException {
	body = new FileInputStream(f);
	bodyLength = f.length();
	mimeType = Http.getMimeTypeByFileName(f.getName());
    }

    public void serialize(OutputStream responseStream) {
	PrintStream ps = new PrintStream(responseStream);
	print(ps, "HTTP/1.1 " + status.code() + " " + status.reason());
	print(ps, "Content-Type: " + mimeType);

	if (bodyLength > 0) {
	    print(ps, "Content-Size: " + bodyLength);
	}

	print(ps, "");

	byte[] buffer = new byte[1024];
	int read;
	try {
	    while ((read = body.read(buffer)) >= 0) {
		ps.write(buffer, 0, read);
	    }
	} catch (IOException ex) {
	    Logger.getLogger(HttpResponse.class.getName()).log(Level.SEVERE, null, ex);
	}
    }
}

class HttpError extends Exception {

    private Http.Status _status;
    
    public HttpError(Http.Status s) {
	_status = s;
    }

    public Http.Status getStatus() {
	return _status;
    }
};
