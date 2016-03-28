package br.com.radiolistener.Listener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.radiolistener.Fingerprinting.IFingerprinter;
import br.com.radiolistener.Fingerprinting.MatchResult;
import br.com.radiolistener.Fingerprinting.EchoprintFingerprinter.LocalFingerprinter;
import br.com.radiolistener.Fingerprinting.EchoprintFingerprinter.RemoteFingerprinter;

import com.json.parsers.JSONParser;
import com.json.parsers.JsonParserFactory;


public class RadioClientManager implements IRadioClientCallback{
	private static Logger log = LoggerFactory.getLogger(RadioClientManager.class);
	private HashMap<Integer,RadioClient> radioClients;
	private Timer t;
	private String echoprintServer;
	private String charset = "UTF-8"; 
	private HashMap <Integer,Integer> radioLogEntries;
	private DataSource ds;	
	private IFingerprinter fp;
	private IFingerprinter rfp;
	
	private int maxLoadedTrid = 0;
	
	
	public RadioClientManager(String echoprintServer, DataSource ds)
	{
		this.echoprintServer = echoprintServer;
		this.ds = ds;
		fp = new LocalFingerprinter();
		rfp = new RemoteFingerprinter(echoprintServer);		
		//load songs into fp
		this.loadTracks(0);
		
	}
		
	public void init(List<Radio> radios)
	{
		log.info("RadioClientManager starting.");
		
		radioClients = new HashMap<Integer,RadioClient>();
		radioLogEntries = new HashMap<Integer,Integer>();
		
		for(Radio radio : radios)
		{
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			RadioClient client = new RadioClient(radio);
			client.setRadioClientCallback(this);
			radioClients.put(radio.getId(), client);
			radioLogEntries.put(radio.getId(),-1);
			client.startThread();
		}
		
		t = new Timer("RadioManagerTimer");
		
		t.schedule(new TimerTask(){
			@Override
			public void run(){
				ressurect();
			}
		}
		 ,5*60*1000,5*60*1000);
		
		t.schedule(new TimerTask(){
			@Override
			public void run(){
				logStatus();
			}
		}
		 ,2*60*1000,2*60*1000);
		
		t.schedule(new TimerTask(){
			@Override
			public void run(){
				updateTracks();
			}
		}
		 ,60*60*1000,60*60*1000);
		
		
	}
	
	
	private void ressurect()
	{
		Date now = new Date();
		for (RadioClient client:radioClients.values())
        {			
			if(client.getState() != ReceiverState.PLAYING || (now.getTime() - client.getLastTake().getTime()) > 60*1000)
    		{
					//something is wrong.
					//let's try restarting it in any case. doesn't hurt to do so every 5 minutes
					try{
						log.info("RadioClientManager restarting radio: " + client.getRadio().getId());
						client.stop();
						client.startThread();
					}catch(Exception ex)
					{
						log.error("RadioClientManager: error ressurecting radio. RadioId: " + client.getRadio().getId() + ex.toString());
					}
					
				
    		}
        }
	}
	
	private void logStatus()
	{
		
		Connection connection = null;
		PreparedStatement insertStatement = null;
		PreparedStatement updateStatement = null;
		ResultSet generatedKeys = null;
		
		try{
			Date now = new Date();
			Timestamp nowts = new Timestamp(now.getTime());
			Timestamp nowless30ts = new Timestamp(now.getTime() -30000);
				
			connection = ds.getConnection();
			
	
			insertStatement = connection.prepareStatement("INSERT INTO RadioStatus (idRadio, StartTime, EndTime, RadioOn) VALUES (?, ?, ?, 1)", Statement.RETURN_GENERATED_KEYS);
			updateStatement = connection.prepareStatement("UPDATE RadioStatus set EndTime = ? WHERE idRadioStatus = ?",Statement.RETURN_GENERATED_KEYS);
			
			for (RadioClient client:radioClients.values())
	        {
				if(client.getState() != ReceiverState.PLAYING || (now.getTime() - client.getLastTake().getTime()) > 60*1000)
	    		{
					if(this.radioLogEntries.get(client.getRadio().getId()) != -1){
						this.radioLogEntries.put(client.getRadio().getId(), -1);
					}					
					
	    		}else
	    		{
	    			if(client.getTakeCount() > 0 && (client.getMatchErrorCount() <= 0.1*client.getTakeCount()))
	    			{   
	    				if(this.radioLogEntries.get(client.getRadio().getId()) == -1)
	    				{
	    					insertStatement.setInt(1, client.getRadio().getId());
	    					insertStatement.setTimestamp(2, nowless30ts);
	    					insertStatement.setTimestamp(3,nowts);
	    					int affectedRows = insertStatement.executeUpdate();
	    					if(affectedRows == 0)
	    					{
	    						log.error("Error creating radio status. No rows inserted. RadioId:" + client.getRadio().getId());
	    					}else
	    					{
	    						generatedKeys = insertStatement.getGeneratedKeys();
	    						if(generatedKeys.next())
	    						{
	    							int newId = generatedKeys.getInt(1);
	    							this.radioLogEntries.put(client.getRadio().getId(),newId);
	    						}else
	    						{
	    							log.error("Error creating radio status. No generated id found." + " RadioId:" + client.getRadio().getId());
	    						}
	    					}	    						    					
	    				}else
	    				{
	    					updateStatement.setTimestamp(1, nowts);
	    					updateStatement.setInt(2, this.radioLogEntries.get(client.getRadio().getId()));
	    					int affectedRows = updateStatement.executeUpdate();
	    					if(affectedRows == 0)
	    					{
	    						log.error("Error updating radio status. No rows updated. idRadioStatus:" + this.radioLogEntries.get(client.getRadio().getId()) + " RadioId:" + client.getRadio().getId());
	    					}    
	    				} 	
	    			}	    			
	    		}
	    			
	        }
		}catch(Exception ex)
		{
			log.error("RadioClientManager: Error logging status- " + ex.toString());
			
		}finally
		{			
			if(generatedKeys != null)
			{				
				try {
					generatedKeys.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}					
			}
			if(insertStatement != null)
			{
				try {
					insertStatement.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(updateStatement != null)
			{
				try {
					updateStatement.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(connection != null)
			{
				try {
					connection.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private void updateTracks()
	{
		loadTracks(this.maxLoadedTrid + 1);
	}
	
	private void loadTracks(int offset)
	{
		log.info("RadioClientManager loading tracks.");
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			connection = ds.getConnection();
			statement = connection.prepareStatement("SELECT idMusica, EchoprintCode, Duracao FROM Musica WHERE idMusica >= ?");
			statement.setInt(1, offset);
			result = statement.executeQuery();
			while(result.next()){
				int trid = result.getInt(1);
				if(trid > this.maxLoadedTrid)
				{
					this.maxLoadedTrid = trid;
				}				
				String fprint = result.getString(2);
				int codestart = fprint.indexOf("\"code\":");
				int codeend = fprint.indexOf("\"", codestart+10);
				
				String code = fprint.substring(codestart+8,codeend);
				int length = result.getInt("Duracao");
				fp.ingest(code, trid, length);
				rfp.ingest(code, trid, length);
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
	}
	
	
	@Override
	public MatchResult onCode(Date date, Radio radio, String code){
		log.debug("RadioClientManager: code received. Radio - " + radio.getId());
		
		//long startTime = System.currentTimeMillis();
			
		MatchResult localResult = fp.bestMatchForQuery(code);		
			
		//long endTime = System.currentTimeMillis();
		//System.out.println("FP time: " + (endTime-startTime));
		
		if(localResult == null || localResult.isError())
		{
			log.error("Radio:" + radio.getId() + " Fingerprinter error.");
			
		}else if(localResult.isMatch())
		{
			log.info("Radio:" + radio.getId() +" - Match found- trid:" + localResult.getTrid());
		}
			return localResult;	
				
	}
	
			
	public HashMap<Integer,RadioClient> getRadioClients()
	{
		return this.radioClients;
	}

	@Override
	public DataSource getDataSource() {
		return this.ds;
	}
	
	
	
	

}
