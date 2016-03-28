package br.com.radiolistener.Listener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.radiolistener.Fingerprinting.MatchResult;


public class RadioClient implements IRadioClient{
	private static Logger log = LoggerFactory.getLogger(RadioClient.class);
	private Radio radio;
	
	private IRadioClientCallback callback;
	private IReceiver receiver;
	private Thread receiverThread;
	private Thread clientThread;
	private volatile boolean clientOn = true;
	private volatile Date lastTake = new Date();
	private volatile int takeCount;
	private volatile int initCount;
	private volatile int matchErrorCount;
	private Hashtable <Integer,PendingMatch> pendingMatches;
	
	//private String codegenPath = "/home/lucas/workspace/JEchoprintLib/bin/";
	
	public RadioClient(Radio radio)
	{
		this.radio = radio;
		pendingMatches = new Hashtable<Integer,PendingMatch>();
	}



	@Override
	public void run() {
		log.info("Radio: " + this.radio.getId() + " - RadioClient starting");
		while(clientOn){
			try {			
				ByteBuffer result = this.receiver.getQueue().take();
				log.debug("Radio: " + this.radio.getId() + " - RadioClient taking buffer");			
				lastTake = new Date();
				takeCount++;
				this.checkPendingMatches();
			
				try {
					
					if(result.limit() > 400000)//has at least 20s of data
					{		
						//this.saveBuffer(result, Integer.toString(this.radio.getId()));
						//result.flip();
						Process proc = null;
						String code = null;
						
						try{
						
							String[] processOptions = new String[] {"echoprint-codegen","-"};
							ProcessBuilder builder = new ProcessBuilder(processOptions);		
							builder.redirectErrorStream(false);
							proc = builder.start();
							InputStream stdout = proc.getInputStream();
							OutputStream stdin = proc.getOutputStream();
							
							byte[] bytes = new byte[result.limit()];
								
							result.get(bytes);
							stdin.write(bytes);
													
							stdin.flush();
							stdin.close();
												
							ByteBuffer out = ByteBuffer.allocate(2024*1024);
							byte[] resultBytes = new byte[1024*1024];
							int bytesRead;
				            while((bytesRead = stdout.read(resultBytes)) != -1){
				            	out.put(resultBytes,0,bytesRead);
				            }
				            
							proc.waitFor();				
							
				           			            
				            out.flip();
							byte[] stringbytes = new byte[out.limit()];
							out.get(stringbytes);
													
							String test = new String(stringbytes);
							
							int codestart = test.indexOf("\"code\":");
							int codeend = test.indexOf("\"", codestart+10);
							
							code = test.substring(codestart+8,codeend);				
				
						}catch(Exception ex){
							log.error("Radio: " + this.radio.getId() + " - Error running codegen." + ex.toString());
							if(ex instanceof InterruptedException)
							{
								throw ex;
							}
						}finally{
							if(proc != null){
								try {
									proc.getInputStream().close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								try {
									proc.getOutputStream().close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								
								try {
									proc.getErrorStream().close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}					
								proc.destroy();
							}
						}						
						
				
						if(code != null && !code.equals("error") && code.length() > 10)
						{
							log.debug("Radio: " + this.radio.getId() + " - Submitting code: " + code);
							MatchResult match = callback.onCode(new Date(),this.radio, code);
							if(match != null && match.isMatch())
							{
								Connection connection = null;
								PreparedStatement insertStatement = null;
								PreparedStatement updateStatement = null;
								ResultSet generatedKeys = null;
								
								try{
									Date now = new Date();									
									Timestamp nowless30ts = new Timestamp(now.getTime() -30000);
									
									connection = this.callback.getDataSource().getConnection();								

									insertStatement = connection.prepareStatement("INSERT INTO Matches (idRadio, idMusica, StartTime, Duration) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
									updateStatement = connection.prepareStatement("UPDATE Matches set Duration = ? WHERE idMatches = ?",Statement.RETURN_GENERATED_KEYS);
									
									if(!this.pendingMatches.containsKey(match.getTrid()))
									{										
										insertStatement.setInt(1,this.radio.getId());
										insertStatement.setInt(2,match.getTrid());
										insertStatement.setTimestamp(3, nowless30ts);
										insertStatement.setInt(4, 30);
										int affectedRows = insertStatement.executeUpdate();
				    					if(affectedRows == 0)
				    					{
				    						log.error("Error inserting new match. RadioId:" + this.radio.getId() + " trid: " + match.getTrid());
				    					}else
				    					{
				    						generatedKeys = insertStatement.getGeneratedKeys();
				    						if(generatedKeys.next())
				    						{
				    							int newId = generatedKeys.getInt(1);
				    							PendingMatch pending = new PendingMatch(newId, this.radio.getId(), match.getTrid(), new Date(), match.getLength(), 30);
				    							this.pendingMatches.put(match.getTrid(), pending);
				    							this.saveBuffer(result, Integer.toString(newId));
				    						}else
				    						{
				    							log.error("Error inserting new match. RadioId:" + this.radio.getId() + " trid: " + match.getTrid());
				    						}
				    					}
										
									}else
									{
										PendingMatch pending = this.pendingMatches.get(match.getTrid());
										pending.setMatchDuration(pending.getMatchDuration() + 30);
										updateStatement.setInt(1, pending.getMatchDuration());
										updateStatement.setInt(2, pending.getIdMatch());
										int affectedRows = updateStatement.executeUpdate();
				    					if(affectedRows == 0)
				    					{
				    						log.error("Error updating match. No rows updated. idMatch:" + pending.getIdMatch() + " RadioId:" + this.radio.getId());
				    					}
									
										
									}
									
								
								}catch(SQLException ex)
								{
									log.error("RadioId:" + this.radio.getId() + "- Error saving match. " + ex.toString());									
									
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
							else if (match == null ||  match.isError())
							{
								log.error("Radio: " + this.radio.getId() + " - Error getting match.");
								matchErrorCount++;
							}
							
						}else
						{
							log.error("Radio: " + this.radio.getId() + " - Error getting fingerprint. Buffer: " + result.toString());
							matchErrorCount++;
							if(code != null && code.length() < 10)
							{
								log.debug("Radio: " + this.radio.getId() + " - fingerprint too short.");
							}
						}
					}else
					{
						matchErrorCount++;
						log.error("Radio: " + this.radio.getId() + " - buffer too short. Length: " + result.limit());
					}
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					log.error("Radio: " + this.radio.getId() + " - Error getting fingerprint: " + e.toString());
					matchErrorCount++;
					if(e instanceof InterruptedException)
					{
						throw e;
					}
				}
				
				
				
			} catch (Throwable e) {
				clientOn = false;
				if(this.receiver != null)
				{
					receiver.disconnect();
					receiver = null;
				}
				log.warn("Radio: " + this.radio.getId() + " - RadioClient Interrupted. " + e.toString());
				Thread.currentThread().interrupt();
				return;				
			}
		}
		
	}
	
	private void checkPendingMatches()
	{
		Date now = new Date();
		List<Integer> toRemove = new LinkedList<Integer>();
		for(PendingMatch pending:this.pendingMatches.values())
		{
			if(pending.getStartTime().getTime() + pending.getTrackDuration()*1000 < now.getTime() -60*1000)
			{
				toRemove.add(pending.getTrid());
			}
		}
		
		for(Integer i:toRemove)
		{
			this.pendingMatches.remove(i);
		}
	}
	
	
	private void saveBuffer(ByteBuffer buffer, String filename){
		File outp = new File(filename + ".raw");		
		FileChannel channel =null;
		
		try {
			channel = new FileOutputStream(outp,false).getChannel();
			if(buffer.position() > 0)
			{
				buffer.flip();
			}
			channel.write(buffer);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally
		{
			if(channel != null){			
				try {
					channel.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}		
	
	}
	
	private void init()
	{
		initCount++;
		lastTake = new Date();
		takeCount = 0;
		matchErrorCount = 0;
		clientOn = true;
		if(radio.getProtocol() == RadioProtocol.RTMP)
		{
			this.receiver = new RTMPReceiver(radio.getHost(),radio.getPort(),radio.getApp(),radio.getFile(),radio.getId());
		}else if(radio.getProtocol() == RadioProtocol.ICY)
		{
			this.receiver = new ICYReceiver(radio.getHost(), radio.getPort(), radio.getApp(), radio.getId());			
		}
		else
		{
			throw new RuntimeException("protocol not implemented");
		}
	}

	@Override
	public Radio getRadio() {
		return this.radio;
	}

	@Override
	public void setRadioClientCallback(IRadioClientCallback callback) {
		this.callback = callback;
		
	}

	@Override
	public void start() {
		init();
		
		receiverThread = new Thread(this.receiver,"ReceiverThread" + radio.getNome());
		
		receiverThread.start();
		this.run();
		
	}

	@Override
	public void startThread() {
		init();
	
		receiverThread = new Thread(this.receiver,"ReceiverThread" + radio.getNome());
		
		receiverThread.start();
		
		clientThread = new Thread(this,"RadioClientThread" + radio.getNome());
		
		clientThread.start();				
	}

	@Override
	public void stop() {
		log.info("Radio: " + this.radio.getId() + " - RadioClient stopping.");
		if(this.receiver != null)
		{
			receiver.disconnect();
			receiver = null;
		}
		clientOn = false;
		if(this.clientThread != null)
		{			
			clientThread.interrupt();
			try {
				clientThread.join();
				clientThread = null;
				receiverThread = null;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}



	@Override
	public Date getLastTake() {
		return this.lastTake;
	}



	@Override
	public ReceiverState getState() {
		if(this.receiver != null)
		{
			return this.receiver.getState();
		}else
		{
			return ReceiverState.STOPPED;
		}
	}



	@Override
	public int getTakeCount() {
		return this.takeCount;
	}



	@Override
	public int getInitCount() {
		return this.initCount;
	}

	@Override
	public int getMatchErrorCount() {
		return this.matchErrorCount;
	}


	@Override
	public int getQueueSize() {
		if(this.receiver != null && this.receiver.getQueue() != null)
			{
				return this.receiver.getQueue().size();
			}else
			{
				return 0;
			}
	}
	
}
