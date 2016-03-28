package br.com.radiolistener.Listener;


import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;

public class StreamGobbler extends Thread{
	private InputStream is;
	private ByteBuffer result;	
	
	public ByteBuffer getResult()
	{
		return this.result;
	}
	
	public StreamGobbler(InputStream is)
	{
		this.is = is;
		result = ByteBuffer.allocate(2024*1024);
	}
	
	@Override
	public void run()
	{
		 try {
	            byte[] temp = new byte[48192];
	            int bytesRead;
	            while((bytesRead = is.read(temp)) != -1){
	            	result.put(temp,0,bytesRead);
	            }
	                
	        }
	        catch (IOException ioe) {
	            ioe.printStackTrace();
	        }
	}
	
	
}
