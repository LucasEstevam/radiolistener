package br.com.radiolistener.Listener;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;


public interface IReceiver extends Runnable {
	BlockingQueue<ByteBuffer> getQueue();
	void disconnect();
	ReceiverState getState();
	int getInterval();
	void setInterval(int value);	
}
