package org.lesscss;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class HttpResource implements Resource {

	private static final String	LESSCSS_FOLDER	= "/.lesscss/";
	URI							url;

	public HttpResource(String url) throws URISyntaxException {
		this.url = new URI(url);
	}

	public HttpResource(URI url) {
		this.url = url;
	}

	public boolean exists() {
		try {
			URL u = url.toURL();
			URLConnection connection = u.openConnection();
			connection.connect();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public long lastModified() {
		try {
			URL u = url.toURL();
			URLConnection connection = u.openConnection();
			return connection.getLastModified();
		} catch (IOException e) {
			return 0;
		}
	}

	public InputStream getInputStream() throws IOException {

		URL u = url.toURL();
		String proto = u.getProtocol();
		switch (proto.toLowerCase()) {
		case "http":
		case "https":
			return getFromCache(u);
		default:
			break;
		}
		return u.openStream();
	}

	private InputStream getFromCache(URL u) throws IOException {
		System.out.println(u);
		String fileName = u.getHost()+"/"+u.getFile();
		String home = System.getProperty("user.home");
		final String pathname = home + LESSCSS_FOLDER + fileName;
		File file = new File(pathname);
		String etag = "";
		try {
			final byte[] attribute = (byte[]) Files.getAttribute(file.toPath(), "user:ETag");
			etag = new String(attribute) + "";
		} catch (Exception notImportant) {
//			e.printStackTrace();
		}
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.addRequestProperty("User-Agent", "Java.LesscssCompiler");

		swapToFile(file, uc, etag);
		
		return new FileInputStream(file);
	}

	private void swapToFile(File file, HttpURLConnection uc, String etag)
			throws IOException, FileNotFoundException {
		uc.addRequestProperty("If-None-Match", etag);
		if (uc.getResponseCode() == 304) {
			return;
		}
		String newEtag = uc.getHeaderField("ETag");
		InputStream is = uc.getInputStream();

		final FileOutputStream output = FileUtils.openOutputStream(file, false);
		IOUtils.copy(is, output, 65535);
		output.close();

		Files.setAttribute(file.toPath(), "user:ETag", ByteBuffer.wrap(newEtag.getBytes()));
	}

	public Resource createRelative(String relativeResourcePath) throws IOException {
		try {
			return new HttpResource(url.resolve(new URI(relativeResourcePath)));
		} catch (URISyntaxException e) {
			throw (IOException) new IOException("Could not resolve " + url + " against " + relativeResourcePath)
					.initCause(e);
		}
	}

	public String getName() {
		return url.toASCIIString();
	}
}
