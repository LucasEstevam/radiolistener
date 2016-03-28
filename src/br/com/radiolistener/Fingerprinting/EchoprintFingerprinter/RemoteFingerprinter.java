package br.com.radiolistener.Fingerprinting.EchoprintFingerprinter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.json.parsers.JSONParser;
import com.json.parsers.JsonParserFactory;

import br.com.radiolistener.Fingerprinting.IFingerprinter;
import br.com.radiolistener.Fingerprinting.MatchResult;
import br.com.radiolistener.Listener.RadioClient;

public class RemoteFingerprinter implements IFingerprinter{
	private static Logger log = LoggerFactory.getLogger(RemoteFingerprinter.class);
	private ConcurrentHashMap <Integer,Integer> songLengths;
	private String charset = "UTF-8"; 
	private String echoprintServer;
	
	
	public RemoteFingerprinter(String echoprintServer)
	{
		this.songLengths = new ConcurrentHashMap<Integer,Integer>();
		this.echoprintServer = echoprintServer;
	}
	
	public List<Integer> getLoadedTracks()
	{
		return new ArrayList<Integer>(this.songLengths.keySet());
	}
	
	
	@Override
	public MatchResult bestMatchForQuery(String code_string) {
		try{
			String query = "fp_code=" + URLEncoder.encode(code_string, charset);
			URLConnection connection = new URL(echoprintServer + "?" + query).openConnection();
			connection.setRequestProperty("Accept-Charset", charset);
			connection.setConnectTimeout(1000);
			connection.setReadTimeout(1000);
			InputStream response = connection.getInputStream();
			int status = ((HttpURLConnection)connection).getResponseCode();
			if(status == 200)
			{				
				InputStreamReader reader = new InputStreamReader(response);
				BufferedReader br = new BufferedReader(reader);
				String result = br.readLine();
				br.close();
				
				JsonParserFactory factory = JsonParserFactory.getInstance();
				JSONParser parser = factory.newJsonParser();
				Map jsonData = parser.parseJson(result);
				int score = Integer.parseInt((String) jsonData.get("score"));
				
				if(score != 0){				
					int trid = Integer.parseInt((String)jsonData.get("track_id"));								
					int length = songLengths.get(trid);
					return new MatchResult(trid, score, length, false, "Match found.");
					
				}else
				{
					return new MatchResult(-1, -1, -1, false, "No match found.");
				}
				
			}else
			{
				log.error("Echoprint http response error");
				return new MatchResult(-1, -1, -1, true, "Echoprint http response error.");
			}
		}catch(IOException ex){
			log.error("Remotefingerprinter exception: " + ex.toString());
			return new MatchResult(-1, -1, -1, true, "Echoprint http response error.");			
		}
	}

	@Override
	public void ingest(String code, int trid, int length) {
		this.songLengths.putIfAbsent(trid, length);
		
	}

}
