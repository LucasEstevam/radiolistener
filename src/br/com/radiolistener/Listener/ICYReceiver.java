package br.com.radiolistener.Listener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ICYReceiver implements IReceiver{
	private static Logger log = LoggerFactory.getLogger(ICYReceiver.class);
	
	
	
	private volatile ReceiverState state;
	private volatile boolean shouldRun;
	private ExecutorService executor;
	private String host;
	private int port;
	private String app;
	private int radioId;	
	private int interval = 30000;
	
	private BlockingQueue<ByteBuffer> queue;	
		
	
	public ICYReceiver(String host, int port, String app, int radioId)
	{
		this.host = host;
		this.port = port;
		this.app = app;
		this.radioId = radioId;
		
		this.state = ReceiverState.STOPPED;
		this.queue = new LinkedBlockingQueue<ByteBuffer>();
		this.executor = Executors.newSingleThreadExecutor();
		
	}

	@Override
	public void run() {
		log.info("Radio: " + this.radioId + " ICY Receiver starting.");
		this.state = ReceiverState.CONNECTING;
		shouldRun = true;
		
		
		Socket socket = null;
		try {
			socket = new Socket(this.host, this.port);
			socket.setSoTimeout(5000);
			OutputStream os = socket.getOutputStream();
			String user_agent = "WinampMPEG/5.09";
			String req="GET " +this.app + " HTTP/1.1\r\nHost: " + this.host + "\r\nuser-agent: "+user_agent+"\r\nIcy-MetaData: 0\r\nConnection: keep-alive\r\n\r\n";
			os.write(req.getBytes());
			InputStream is = socket.getInputStream();
			
			BufferedReader br = new BufferedReader(new InputStreamReader(is));							
			
			int bitrate = 0;
			
			String line = br.readLine();
			String lines = "";
			while(line != null && !line.isEmpty()){
				lines = lines + line;
				if(line.contains("icy-br"))
				{
					try{
						bitrate = Integer.parseInt(line.split(":")[1].trim());
					}catch(Exception ex)
					{
						log.error("Radio: " + this.radioId + " - Error parsing bitrate");
					}
				}
				line = br.readLine();				
			}
			
			if(line != null && bitrate > 0)
			{				
				ByteBuffer buffer = ByteBuffer.allocate((bitrate*1024/8) * ((this.interval/1000) + 5)); //bytes per 1 sec times (seconds in interval + 5)
				byte[] b = new byte[bitrate*1024/8]; //reads up to 1s
				
				
				this.state = ReceiverState.PLAYING;
				int bytesread = is.read(b);
				
				while(shouldRun && bytesread >0)
				{								
					buffer.put(b,0,bytesread);
					
					if(buffer.position() >= 30*(bitrate*1024/8))
					{
						//buffer has more than 30s data.
						buffer.flip();
						
						Process proc = null;
						boolean interrupted = false;
						ProcessWaiter waiter = null;
						try{
							String[] processOptions = new String[] {"avconv","-loglevel", "quiet", "-i", "-", "-f", "s16le", "-ac", "1", "-ar", "11025", "-"};
							ProcessBuilder builder = new ProcessBuilder(processOptions);		
							builder.redirectErrorStream(false);
							proc = builder.start();
							InputStream stdout = proc.getInputStream();
							OutputStream stdin = proc.getOutputStream();
							CallableStreamGobbler gobbler = new CallableStreamGobbler(stdout);
							
							Future<ByteBuffer> fut = this.executor.submit(gobbler);
							
							byte[] bytes = new byte[buffer.limit()];
								
							buffer.get(bytes);
							stdin.write(bytes);				
													
							stdin.flush();			
							stdin.close();
							
							ByteBuffer result = fut.get();
							
							waiter = new ProcessWaiter(proc);
							waiter.start();
											
							waiter.join(1000);				
							if(waiter.exit != null)
							{
								//waiter has finished waiting for the process
								result.flip();
								
								if(queue.size() > 2)
								{
									log.error("Radio: " + this.radioId + " - Receiver queue size increase detected. Discarding result.");
								}else
								{
									this.queue.put(result);
								}
								
							}else
							{					
								//timeout waiting for avconv.
								log.error("Radio: " + this.radioId + " - Error converting buffer. Timeout waiting for avconv.");
								waiter.interrupt();
								
							}
							
						}catch(InterruptedException ex)
						{
							log.error("Radio: " + this.radioId + " - Error converting buffer. Interrupted waiting for avconv. " + ex.toString());
							if(waiter != null)
							{
								waiter.interrupt();
							}
							interrupted = true;			
						} catch (IOException e) {
							log.error("Radio: " + this.radioId + " - Error converting buffer. " + e.toString());
						} catch (ExecutionException e) {
							log.error("Radio: " + this.radioId + " - Error converting buffer. " + e.toString());
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
								if(interrupted)
								{
									Thread.currentThread().interrupt();
									return;
								}
							}
						}
						
						buffer.clear();
						
					}
					
					bytesread = is.read(b);
					
				}
				
				this.state = ReceiverState.STOPPED;
				
			}else
			{
				log.error("Radio: " + this.radioId + " ICY Receiver error. Empty stream or failed to get bitrate.");
				this.state = ReceiverState.STOPPED;
			}
			
			
			
		} catch (UnknownHostException e) {
			log.error("Radio: " + this.radioId + " ICY Receiver error. Unknown host.");			
			this.state = ReceiverState.STOPPED;
		} catch (IOException e) {
			log.error("Radio: " + this.radioId + " ICY Receiver error." + e.toString());
			this.state = ReceiverState.STOPPED;			
		}finally
		{
			this.state = ReceiverState.STOPPED;
			if(socket != null)
			{
				try {
					socket.close();
				} catch (IOException e) {
					//ignore cleanup exception
					e.printStackTrace();
				}
			}
			
		}
		
		
		
		
		
	}

	@Override
	public BlockingQueue<ByteBuffer> getQueue() {
		return this.queue;
	}

	@Override
	public void disconnect() {
		log.info("Radio: " + this.radioId + " ICY Receiver disconnecting.");
		
		shouldRun = false;
		executor.shutdown();
		
	}

	@Override
	public ReceiverState getState() {
		return this.state;
	}

	@Override
	public int getInterval() {
		return this.interval;
	}

	@Override
	public void setInterval(int value) {
		this.interval = value;
		
	}
	
	

}
