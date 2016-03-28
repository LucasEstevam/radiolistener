package br.com.radiolistener.Fingerprinting.EchoprintFingerprinter;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.radiolistener.Fingerprinting.IFingerprinter;
import br.com.radiolistener.Fingerprinting.MatchResult;


public class LocalFingerprinter implements IFingerprinter {
	private static Logger log = LoggerFactory.getLogger(LocalFingerprinter.class);
	
	private ConcurrentHashMap<Integer,Fingerprint> store;
	private ConcurrentHashMap<Integer,CopyOnWriteArrayList<Integer>> index;
	private ConcurrentHashMap <Integer,Integer> songLengths;

	private Base64 decoder;
	
	private final int ELBOW = 90;
	private final int LIMIT = 30;
	private final double THRESHOLD = 0.08;
	private final double SLOP = 2;
	private final int MAXDIST = 32767;
	
	
	
		public LocalFingerprinter()
		{
			store = new ConcurrentHashMap<Integer,Fingerprint>();
			index = new ConcurrentHashMap<Integer,CopyOnWriteArrayList<Integer>>();
			songLengths = new ConcurrentHashMap<Integer,Integer>();
						
			decoder = new Base64(true);						
			
		}
		
		
		public List<Integer> getLoadedTracks()
		{
			return new ArrayList<Integer>(this.store.keySet());
		}
		
		public void ingest(String code, int trid, int length){
			log.info("LocalFingerprinter ingesting track: " + trid);
			Fingerprint fp = decodeCodeString(code);
			store.putIfAbsent(trid, fp);
			songLengths.putIfAbsent(trid, length);
			for(int i = 0; i < fp.codes.length; i++){
				if(!index.containsKey(fp.codes[i]))
				{
					index.put(fp.codes[i], new CopyOnWriteArrayList<Integer>());
				}
				index.get(fp.codes[i]).addIfAbsent(trid);
			}			
		}
		
		public MatchResult bestMatchForQuery(String code_string)
		{
			int result = -1;
			Fingerprint fp = decodeCodeString(code_string);
			
			
			if(fp != null){
				int codeLen = fp.codes.length;
				if(codeLen < ELBOW)
				{
					//code too short
					return new MatchResult(-1, -1,-1, true, "Code too short.");
				}
				List<Map.Entry<Integer, Integer>> response = this.localQuery(fp);
				if(response.size() == 0)
				{
					//no tracks fount
					return new MatchResult(-1, -1,-1, false, "No tracks found.");
				}else
				{
					int topMatchScore = response.get(0).getValue();
					
					if(topMatchScore < codeLen*THRESHOLD)
					{
						//score too low
						return new MatchResult(-1, -1,-1, false, "Score below threshold.");
					}
					
					HashMap<Integer,Integer> actualScores = new HashMap<Integer,Integer>(); 
					HashMap<Integer,Integer> oldScores = new HashMap<Integer,Integer>();
					
					for(Map.Entry<Integer, Integer> entry:response)
					{
						int trid = entry.getKey();
						oldScores.put(trid, entry.getValue());
						int actualScore = computeActualScore(fp,store.get(trid));
						actualScores.put(trid,actualScore);						
					}
					
					List<Map.Entry<Integer, Integer>> sortedActualScores = new ArrayList<Map.Entry<Integer, Integer>>(actualScores.entrySet());
					Collections.sort(sortedActualScores, new Comparator<Map.Entry<Integer,Integer>>(){
						public int compare(Map.Entry<Integer,Integer> o1, Map.Entry<Integer,Integer> o2){
							return o2.getValue().compareTo(o1.getValue());
						}
						
					});
					
					
					if(sortedActualScores.size() == 1)
					{
						int topScore = sortedActualScores.get(0).getValue();
						int topTrid = sortedActualScores.get(0).getKey();
						if(topScore >= THRESHOLD*2*codeLen)
						{
							//single good match.
							return new MatchResult(topTrid, topScore,this.songLengths.get(topTrid), false, "Single good match.");							
														
						}else
						{
							//single bad match
							return new MatchResult(-1, -1,-1, false, "Single bad match.");
							
						}
					}else
					{
						int topScore = sortedActualScores.get(0).getValue();
						int topTrid = sortedActualScores.get(0).getKey();
						int top2ndScore = sortedActualScores.get(1).getValue();
						int top2ndTrid = sortedActualScores.get(1).getKey();
						
						if(topScore >= THRESHOLD*codeLen)
						{
							//single good match.
							
 							if(topScore > (oldScores.get(topTrid)/6))
							{
								if((topScore-top2ndScore) >= (topScore/3))
								{
									return new MatchResult(topTrid, topScore,this.songLengths.get(topTrid), false, "Multiple good match.");
								}else
								{
									//first two scores are too similar
									return new MatchResult(-1, -1,-1, false, "Scores too similar.");
								}
								
							}else
							{
								//multiple bad match(actualScore decreased by too much
								return new MatchResult(-1, -1,-1, false, "Multiple bad matches (actualScore decreased by too much.");
							}
								
														
														
						}else
						{
							//multiple bad match
							return new MatchResult(-1, -1,-1, false, "Multiple bad matches.");
							
						}
						
					}					
					
				}
				
			}else
			{
				//could not decode
				return new MatchResult(-1, -1,-1, true, "Could not decode.");
			}			
			
		}
		
		
		private int min(int[] x)
		{
			if(x.length > 0)
			{
				int minValue = x[0];
				for(int i = 0; i < x.length; i++)
				{
					if(x[i] < minValue)
					{
						minValue = x[i];
					}
				}
				return minValue;
			}else
			{
				throw new RuntimeException("Min function: Empty Array");
			}
		}
		
		private boolean contains(int[] array, int x)
		{
			if(array.length > 0)
			{
				for(int i = 0; i< array.length; i++)
				{
					if(array[i] == x)
					{
						return true;
					}
				}
				return false;
				
			}else
			{
				throw new RuntimeException("Contains function: Empty Array");
			}
		}
		
		private int computeActualScore(Fingerprint code, Fingerprint track_code)
		{
			
			HashMap<Integer,ArrayList<Integer>> queryCodes = new HashMap<Integer,ArrayList<Integer>>();
			HashMap<Integer,Integer> timeDiffs = new HashMap<Integer,Integer>();
			int minTime = min(code.times);
			
			//map times to code occurences in query
			for(int i = 0; i < code.codes.length; i ++)
			{
				int qtime = (int) ((code.times[i] - minTime) / SLOP);
				if(!queryCodes.containsKey(code.codes[i]))
				{
					queryCodes.put(code.codes[i], new ArrayList<Integer>());
				}
				queryCodes.get(code.codes[i]).add(qtime);
			}
			
				
			for(int i = 1; i<track_code.codes.length; i++)
			{
				if(contains(code.codes,track_code.codes[i]))
				{
					int matchCodeTime = (int)(track_code.times[i]/SLOP);
					int minDist = MAXDIST;
					//code is present in query.
					for(int qtime:queryCodes.get(track_code.codes[i]))
					{
						int dist = matchCodeTime - qtime;
						if(dist < minDist)
						{
							minDist = dist;
						}
					}
					
					if(minDist < MAXDIST)
					{
						if(!timeDiffs.containsKey(minDist))
						{
							timeDiffs.put(minDist, 1);
						}else
						{
							timeDiffs.put(minDist, 1 + timeDiffs.get(minDist));
						}
					}									
				}	
			}
			//sort histogram	
			List<Map.Entry<Integer, Integer>> list = new ArrayList<Map.Entry<Integer, Integer>>(timeDiffs.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<Integer,Integer>>(){
				public int compare(Map.Entry<Integer,Integer> o1, Map.Entry<Integer,Integer> o2){
					return o2.getValue().compareTo(o1.getValue());
				}
				
			});
			
			if(list.size() > 1)
			{
				return (list.get(0).getValue() + list.get(1).getValue());
			}else if (list.size() > 0)
			{
				return list.get(0).getValue();
			}else
			{
				return 0;
			}
			
		}		
		
		public List<Map.Entry<Integer, Integer>> localQuery(Fingerprint fp)
		{
			HashMap<Integer,Integer> topMatches = new HashMap<Integer,Integer>();
			
			LinkedList<Integer> uniqueKeys = new LinkedList<Integer>();	
			for(int i = 0; i < fp.codes.length; i++)
			{
				if(!uniqueKeys.contains(fp.codes[i]))
				{
					uniqueKeys.add(fp.codes[i]);
					if(index.get(fp.codes[i]) != null)
					{
						for(Integer x:index.get(fp.codes[i]))
						{
							if(!topMatches.containsKey(x))
							{
								topMatches.put(x , 1);
							}else
							{
								topMatches.put(x, topMatches.get(x) + 1);
							}
						}
					}
				}
			}
			
			List<Map.Entry<Integer, Integer>> list = new ArrayList<Map.Entry<Integer, Integer>>(topMatches.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<Integer,Integer>>(){
				public int compare(Map.Entry<Integer,Integer> o1, Map.Entry<Integer,Integer> o2){
					return o2.getValue().compareTo(o1.getValue());
				}
				
			});
			
			return list.subList(0, Math.min(list.size(), LIMIT));
			
		}
		
		
		public Fingerprint decodeCodeString(String code)
		{
			try {
				
				byte[] input = decoder.decode(code);
				Inflater decompresser = new Inflater();			
				decompresser.setInput(input);
				byte[] decomp = new byte[1024*1024];				
				int resultLength = decompresser.inflate(decomp);
				decompresser.end();
				String result = new String(decomp,0,resultLength,"UTF-8");
				return inflateCodeString(result);
				
			} catch (Exception e) {
				log.error("Error decoding code: " + e.toString());
				return null;
			}
		}
		
		private Fingerprint inflateCodeString(String s)
		{			
			int n = (int) Math.floor(s.length()/10);
			Fingerprint fp = new Fingerprint();
			fp.codes = new int[n];
			fp.times = new int[n];
			for(int i = 0; i < n; i++)
			{
				fp.codes[i] = (Integer.parseInt(s.substring((i+n)*5,(i+n)*5+5),16));				
				fp.times[i] = (Integer.parseInt(s.substring(i*5,i*5+5),16));				
			}			
			return fp;
		}
		
		
		
}
