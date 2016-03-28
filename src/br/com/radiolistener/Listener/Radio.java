package br.com.radiolistener.Listener;

public class Radio {
	private int id;
	private String nome;
	private String host;
	private int port;
	private String app;
	private String file;
	private RadioProtocol protocol;
	
	public String getNome() {
		return nome;
	}
	public void setNome(String nome) {
		this.nome = nome;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public String getApp() {
		return app;
	}
	public void setApp(String app) {
		this.app = app;
	}
	public String getFile() {
		return file;
	}
	public void setFile(String file) {
		this.file = file;
	}
	public RadioProtocol getProtocol() {
		return protocol;
	}
	public void setProtocol(RadioProtocol protocol) {
		this.protocol = protocol;
	}
	
	public int getId(){
		return this.id;
	}
	
	public void setId(int id){
		this.id= id;
	}
	
	
}
