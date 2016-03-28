package br.com.radiolistener.Listener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class Listener {
	private static Logger log = LoggerFactory.getLogger(Listener.class);
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		Properties prop = new Properties();
		try {
			prop.load(Listener.class.getClassLoader().getResourceAsStream("config.properties"));
		} catch (FileNotFoundException e1) {
			log.error("Config file not found. Exiting");
			return;
		} catch (IOException e1) {
			log.error("Error reading config file. Exiting");
			return;
		}
		
		String listenerTag = System.getProperty("listener.tag");
		System.out.println("Listener Tag: " + listenerTag);
		
		
		//getting configuration from file	
		String db = prop.getProperty("database");
		String username = prop.getProperty("username");
		String password = prop.getProperty("password");
		String echoprintServer = prop.getProperty("echoprintserver");
		
		
		
		//Setting up db connection pool
		HikariConfig config = new HikariConfig();
		config.setMaximumPoolSize(2);
		config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
		config.addDataSourceProperty("url", db);
		config.addDataSourceProperty("user", username);
		config.addDataSourceProperty("password", password);
		config.setUseInstrumentation(false);
		
		HikariDataSource ds = new HikariDataSource(config);		
		
		List<Radio> radios = new LinkedList<Radio>();		
		
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			connection = ds.getConnection();
			statement = connection.prepareStatement("SELECT * FROM testdb.Radio WHERE idRadio >= (SELECT StartID from testdb.Listeners WHERE Tag = ?) AND idRadio <= (SELECT EndID from testdb.Listeners WHERE Tag = ?) AND Enabled = 1");
			statement.setString(1, listenerTag);
			statement.setString(2, listenerTag);
			result = statement.executeQuery();
			while(result.next()){
				int idRadio = result.getInt("idRadio");
				String nome = result.getString("Nome");
				String protocolo = result.getString("Protocolo");
				String streamingHost = result.getString("StreamingHost");
				int streamingPort = result.getInt("StreamingPort");
				String streamingApp = result.getString("StreamingApp");
				String streamingFile = result.getString("StreamingFile");
				Radio radio = new Radio();
				radio.setId(idRadio);
				radio.setNome(nome);
				radio.setProtocol(RadioProtocol.valueOf(protocolo));
				radio.setHost(streamingHost);
				radio.setPort(streamingPort);
				radio.setApp(streamingApp);
				radio.setFile(streamingFile);
				radios.add(radio);				
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally{
			try {
				if(result != null)
				{
					result.close();
				}
				
				if(connection != null)
				{
						connection.close();
				}
				if(statement != null)
				{
					statement.close();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		RadioClientManager manager = new RadioClientManager(echoprintServer, ds);
		manager.init(radios);
		Server server = new Server(8080);
		try {
				
			ManagerWebHandler handler = new ManagerWebHandler(manager);
			server.setHandler(handler);
			server.start();		
			server.join();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
